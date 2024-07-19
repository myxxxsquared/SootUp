package qilin.pta.toolkits.debloaterx;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import qilin.core.PTA;
import qilin.core.builder.MethodNodeFactory;
import qilin.core.builder.callgraph.Edge;
import qilin.core.builder.callgraph.OnFlyCallGraph;
import qilin.core.pag.*;
import qilin.util.PTAUtils;
import qilin.util.Stopwatch;
import qilin.util.queue.QueueReader;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.common.expr.AbstractInstanceInvokeExpr;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.model.SootClass;
import sootup.core.model.SootField;
import sootup.core.model.SootMethod;
import sootup.core.signatures.MethodSubSignature;
import sootup.core.types.ArrayType;
import sootup.core.types.ClassType;
import sootup.core.types.Type;

public class XUtility {
  protected final PTA pta;
  protected final PAG pag;

  protected final Map<AllocNode, HeapContainerQuery> o2HCQ = new ConcurrentHashMap<>();
  /* records objects and their instance fields */
  protected final Map<AllocNode, Set<SparkField>> o2Fields = new ConcurrentHashMap<>();
  protected final Map<Type, Set<SparkField>> t2Fields = new ConcurrentHashMap<>();
  protected final Map<AllocNode, Map<SparkField, Set<VarNode>>> o2nonThisFStores =
      new ConcurrentHashMap<>();
  protected final Map<Type, Map<SparkField, Set<VarNode>>> t2nonThisFStores =
      new ConcurrentHashMap<>();
  protected final Map<AllocNode, Map<SparkField, Set<VarNode>>> o2nonThisFLoads =
      new ConcurrentHashMap<>();
  protected final Map<Type, Map<SparkField, Set<VarNode>>> t2nonThisFLoads =
      new ConcurrentHashMap<>();

  /* records the methods and their receiver objects and types */
  protected final Map<AllocNode, Set<SootMethod>> o2InvokedMethods = new HashMap<>();
  protected final Map<Type, Set<SootMethod>> t2InvokedMethods = new HashMap<>();
  protected final Map<SootMethod, Set<AllocNode>> m2receiverObjects = new HashMap<>();

  protected final Set<Type> rawOrPolyTypes = new HashSet<>();
  protected final XPAG xpag;
  protected final InterFlowAnalysis interfa;

  public XUtility(PTA pta) {
    this.pta = pta;
    this.pag = pta.getPag();
    Stopwatch stopwatch = Stopwatch.newAndStart("HackUtility construction");
    buildHeapFieldsMapping();
    buildHeapMethodsMapping();
    computeRawOrPolyTypes();
    this.xpag = new XPAG(pta, this);
    this.interfa = new InterFlowAnalysis(this);
    stopwatch.stop();
    System.out.println(stopwatch);
  }

  public PTA getPta() {
    return pta;
  }

  public XPAG getXpag() {
    return xpag;
  }

  public InterFlowAnalysis getInterFlowAnalysis() {
    return interfa;
  }

  private boolean isImpreciseType(Type type) {
    if (type == pta.getView().getIdentifierFactory().getClassType("java.lang.Object")) {
      return true;
    }
    if (type instanceof ClassType) {
      ClassType refType = (ClassType) type;
      SootClass sc = pta.getView().getClass(refType).get();
      // TODO: [ms] startwith Abstract? wth.. check if that is correct e.g. in a limited use case
      return sc.isAbstract() || sc.isInterface() || refType.getClassName().startsWith("Abstract");
    }
    return false;
  }

  /* Implemnting the rules for defining coarse types (Figure 6 in the paper) */
  public boolean isCoarseType(Type type) {
    if (type instanceof ArrayType) {
      ArrayType at = (ArrayType) type;
      type = at.getElementType();
    }
    return isImpreciseType(type) || rawOrPolyTypes().contains(type);
  }

  private void computeRawOrPolyTypes() {
    Set<Type> types = new HashSet<>();
    for (AllocNode heap : pag.getAllocNodes()) {
      Type type = heap.getType();
      if (type instanceof ArrayType) {
        ArrayType at = (ArrayType) type;
        Type et = at.getElementType();
        if (isImpreciseType(et)) {
          rawOrPolyTypes.add(et);
        } else {
          types.add(et);
        }
      } else {
        for (SparkField field : getFields(heap)) {
          Type ft = field.getType();
          if (ft instanceof ArrayType) {
            ArrayType fat = (ArrayType) ft;
            ft = fat.getElementType();
          }
          if (isImpreciseType(ft)) {
            rawOrPolyTypes.add(ft);
            rawOrPolyTypes.add(type);
          } else {
            types.add(type);
            types.add(ft);
          }
        }
      }
    }
    boolean continueUpdating = true;
    while (continueUpdating) {
      continueUpdating = false;
      for (Type type : types) {
        for (SparkField field : getFields(type)) {
          Type ft = field.getType();
          if (isCoarseType(ft)) {
            if (rawOrPolyTypes.add(type)) {
              continueUpdating = true;
            }
          }
        }
      }
    }
  }

  private Set<Type> rawOrPolyTypes() {
    return rawOrPolyTypes;
  }

  /* record objects and their fields */
  private void buildHeapFieldsMappingIn(SootMethod method) {
    MethodPAG srcmpag = pag.getMethodPAG(method);
    MethodNodeFactory srcnf = srcmpag.nodeFactory();
    LocalVarNode thisRef = (LocalVarNode) srcnf.caseThis();
    Set<FieldRefNode> stores = new HashSet<>();
    Set<FieldRefNode> loads = new HashSet<>();
    Set<Node> thisAliases = new HashSet<>();
    thisAliases.add(thisRef);
    QueueReader<Node> reader = srcmpag.getInternalReader().clone();
    while (reader.hasNext()) {
      Node from = reader.next(), to = reader.next();
      if (from instanceof LocalVarNode) {
        if (to instanceof FieldRefNode) {
          FieldRefNode frn = (FieldRefNode) to;
          stores.add(frn);
        }
        if (thisAliases.contains(from) && to instanceof LocalVarNode) {
          thisAliases.add(to);
        }
      } else if (from instanceof FieldRefNode) {
        FieldRefNode frn = (FieldRefNode) from;
        loads.add(frn);
      }
    }
    // handle STORE
    for (FieldRefNode frn : stores) {
      LocalVarNode storeBase = (LocalVarNode) frn.getBase();
      SparkField field = frn.getField();
      boolean isNonthisBase = !thisAliases.contains(storeBase);
      for (AllocNode heap : pta.reachingObjects(storeBase).toCIPointsToSet().toCollection()) {
        o2Fields.computeIfAbsent(heap, k -> ConcurrentHashMap.newKeySet()).add(field);
        t2Fields.computeIfAbsent(heap.getType(), k -> ConcurrentHashMap.newKeySet()).add(field);
        if (isNonthisBase) {
          Map<SparkField, Set<VarNode>> f2bs =
              o2nonThisFStores.computeIfAbsent(heap, k -> new ConcurrentHashMap<>());
          f2bs.computeIfAbsent(field, k -> ConcurrentHashMap.newKeySet()).add(storeBase);
          Map<SparkField, Set<VarNode>> f2bsx =
              t2nonThisFStores.computeIfAbsent(heap.getType(), k -> new ConcurrentHashMap<>());
          f2bsx.computeIfAbsent(field, k -> ConcurrentHashMap.newKeySet()).add(storeBase);
        }
      }
    }
    // handle LOAD
    for (FieldRefNode frn : loads) {
      LocalVarNode loadBase = (LocalVarNode) frn.getBase();
      SparkField field = frn.getField();
      boolean isNonthisBase = !thisAliases.contains(loadBase);
      for (AllocNode heap : pta.reachingObjects(loadBase).toCIPointsToSet().toCollection()) {
        o2Fields.computeIfAbsent(heap, k -> ConcurrentHashMap.newKeySet()).add(field);
        t2Fields.computeIfAbsent(heap.getType(), k -> ConcurrentHashMap.newKeySet()).add(field);
        if (isNonthisBase) {
          Map<SparkField, Set<VarNode>> f2bs =
              o2nonThisFLoads.computeIfAbsent(heap, k -> new ConcurrentHashMap<>());
          f2bs.computeIfAbsent(field, k -> ConcurrentHashMap.newKeySet()).add(loadBase);
          Map<SparkField, Set<VarNode>> f2bsx =
              t2nonThisFLoads.computeIfAbsent(heap.getType(), k -> new ConcurrentHashMap<>());
          f2bsx.computeIfAbsent(field, k -> ConcurrentHashMap.newKeySet()).add(loadBase);
        }
      }
    }
  }

  private void buildHeapFieldsMapping() {
    pta.getNakedReachableMethods().stream()
        .filter(PTAUtils::hasBody)
        .forEach(this::buildHeapFieldsMappingIn);
  }

  /* records objects (together with their types) and their invoked methods */
  private void buildHeapMethodsMapping() {
    OnFlyCallGraph callgraph = pta.getCallGraph();
    // collect virtual callsites.
    Set<VirtualCallSite> vcallsites = new HashSet<>();
    for (Edge edge : callgraph) {
      SootMethod srcM = edge.src();
      SootMethod tgtM = edge.tgt();
      if (tgtM.isStatic() || !PTAUtils.hasBody(tgtM)) {
        continue;
      }
      final Stmt s = edge.srcStmt();
      AbstractInvokeExpr ie = s.getInvokeExpr();
      if (ie instanceof AbstractInstanceInvokeExpr) {
        AbstractInstanceInvokeExpr iie = (AbstractInstanceInvokeExpr) ie;
        Local base = iie.getBase();
        LocalVarNode receiver = pag.findLocalVarNode(srcM, base, base.getType());
        MethodSubSignature subSig = iie.getMethodSignature().getSubSignature();
        VirtualCallSite virtualCallSite =
            new VirtualCallSite(
                receiver,
                s,
                new ContextMethod(srcM, pta.emptyContext()),
                iie,
                subSig,
                qilin.core.builder.callgraph.Edge.ieToKind(iie));
        vcallsites.add(virtualCallSite);
      } else {
        throw new RuntimeException("ie could not be of " + ie.getClass());
      }
    }
    // foreach virtualcallsite, we build mapping from their receiver objects.
    for (VirtualCallSite vcallsite : vcallsites) {
      AbstractInstanceInvokeExpr iie = vcallsite.iie();
      Local base = iie.getBase();
      LocalVarNode receiver =
          pag.findLocalVarNode(vcallsite.container().method(), base, base.getType());
      for (AllocNode heap : pta.reachingObjects(receiver).toCIPointsToSet().toCollection()) {
        QueueReader<SootMethod> reader = pta.getCgb().dispatch(heap.getType(), vcallsite);
        while (reader.hasNext()) {
          SootMethod tgtM = reader.next();
          m2receiverObjects.computeIfAbsent(tgtM, k -> new HashSet<>()).add(heap);
          o2InvokedMethods.computeIfAbsent(heap, k -> new HashSet<>()).add(tgtM);
          t2InvokedMethods.computeIfAbsent(heap.getType(), k -> new HashSet<>()).add(tgtM);
        }
      }
    }
  }

  // Below are public APIs for access.

  public Set<AllocNode> getReceiverObjects(SootMethod method) {
    return m2receiverObjects.getOrDefault(method, Collections.emptySet());
  }

  public Set<SootMethod> getInvokedMethods(AllocNode heap) {
    return o2InvokedMethods.getOrDefault(heap, Collections.emptySet());
  }

  /* get or create TPAG for a given type */
  public HeapContainerQuery getHCQ(AllocNode heap) {
    HeapContainerQuery hcq = o2HCQ.get(heap);
    if (hcq == null) {
      hcq = new HeapContainerQuery(this, heap);
      o2HCQ.put(heap, hcq);
    }
    return hcq;
  }

  public Set<SparkField> getFields() {
    Set<SparkField> tmp =
        this.o2Fields.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
    Set<SparkField> ret = new HashSet<>();
    for (SparkField field : tmp) {
      Type type = field.getType();
      if (isCoarseType(type)) {
        ret.add(field);
      }
    }
    return ret;
  }

  public Set<SparkField> getFields(AllocNode heap) {
    return this.o2Fields.getOrDefault(heap, Collections.emptySet());
  }

  public Set<SparkField> getFields(Type type) {
    if (type instanceof ClassType) {
      ClassType refType = (ClassType) type;
      Set<SparkField> ret = this.t2Fields.get(refType);
      if (ret != null) {
        return ret;
      } else {
        ret = this.t2Fields.computeIfAbsent(refType, k -> new HashSet<>());
        for (AllocNode heap : this.o2Fields.keySet()) {
          if (PTAUtils.canStoreType(pta.getView(), heap.getType(), refType)) {
            for (SparkField sparkField : this.o2Fields.get(heap)) {
              if (sparkField instanceof Field) {
                Field f = (Field) sparkField;
                SootField sf = f.getField();
                Type declType = sf.getDeclaringClassType();
                if (PTAUtils.canStoreType(pta.getView(), type, declType)) {
                  ret.add(sparkField);
                }
              } else {
                throw new RuntimeException(sparkField + ";" + sparkField.getClass());
              }
            }
          }
        }
        return ret;
      }
    } else {
      return Collections.emptySet();
    }
  }

  public boolean hasNonThisStoreOnField(AllocNode heap, SparkField field) {
    Map<SparkField, Set<VarNode>> field2bases =
        this.o2nonThisFStores.getOrDefault(heap, Collections.emptyMap());
    return field2bases.containsKey(field);
  }

  public boolean hasNonThisLoadFromField(AllocNode heap, SparkField field) {
    Map<SparkField, Set<VarNode>> field2bases =
        this.o2nonThisFLoads.getOrDefault(heap, Collections.emptyMap());
    return field2bases.containsKey(field);
  }
}
