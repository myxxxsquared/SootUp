package qilin.pta.toolkits.debloaterx;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import qilin.core.PTA;
import qilin.core.pag.AllocNode;
import qilin.core.pag.FieldRefNode;
import qilin.core.pag.LocalVarNode;
import qilin.core.pag.MethodPAG;
import qilin.core.pag.Node;
import qilin.core.pag.PAG;
import qilin.core.pag.SparkField;
import qilin.util.PTAUtils;
import qilin.util.queue.QueueReader;
import sootup.core.model.SootClass;
import sootup.core.model.SootMethod;
import sootup.core.types.ArrayType;
import sootup.core.types.ClassType;
import sootup.core.types.Type;

/*
 * This is a much simpler scheme for finding context-dependent objects.
 * This file does not belong to any part of DebloaterX.
 * (1) a container type is a class which has a `java.lang.Object` field or a container type field,
 * or it implements `java.util.Collection` or is nested in a class implementing `java.util.Collection`.
 * (2) an object is context-dependent if it is of a container type.
 * */
public class CollectionHeuristic {
  protected final PTA pta;
  protected final PAG pag;

  protected final Map<Type, Set<SparkField>> t2Fields = new ConcurrentHashMap<>();

  protected final Set<Type> containerType = ConcurrentHashMap.newKeySet();

  protected final Set<AllocNode> ctxDepHeaps = ConcurrentHashMap.newKeySet();

  public Set<AllocNode> getCtxDepHeaps() {
    return ctxDepHeaps;
  }

  public CollectionHeuristic(PTA pta) {
    this.pta = pta;
    this.pag = pta.getPag();
  }

  private void buildHeapFieldsMappingIn(SootMethod method) {
    MethodPAG srcmpag = pag.getMethodPAG(method);
    Set<FieldRefNode> stores = new HashSet<>();
    Set<FieldRefNode> loads = new HashSet<>();
    QueueReader<Node> reader = srcmpag.getInternalReader().clone();
    while (reader.hasNext()) {
      Node from = reader.next(), to = reader.next();
      if (from instanceof LocalVarNode) {
        if (to instanceof FieldRefNode) {
          FieldRefNode frn = (FieldRefNode) to;
          stores.add(frn);
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
      for (AllocNode heap : pta.reachingObjects(storeBase).toCIPointsToSet().toCollection()) {
        t2Fields.computeIfAbsent(heap.getType(), k -> ConcurrentHashMap.newKeySet()).add(field);
      }
    }
    // handle LOAD
    for (FieldRefNode frn : loads) {
      LocalVarNode loadBase = (LocalVarNode) frn.getBase();
      SparkField field = frn.getField();
      for (AllocNode heap : pta.reachingObjects(loadBase).toCIPointsToSet().toCollection()) {
        t2Fields.computeIfAbsent(heap.getType(), k -> ConcurrentHashMap.newKeySet()).add(field);
      }
    }
  }

  private void buildHeapFieldsMapping() {
    pta.getNakedReachableMethods().stream()
        .filter(PTAUtils::hasBody)
        .forEach(this::buildHeapFieldsMappingIn);
  }

  private boolean isImplementingCollection(SootClass sc) {
    Set<ClassType> allInterfaces = new HashSet<>(sc.getInterfaces());
    while (sc.hasSuperclass()) {
      ClassType classType = sc.getSuperclass().get();
      sc = pta.getView().getClass(classType).get();
      allInterfaces.addAll(sc.getInterfaces());
    }
    // interface may also have super class
    Set<SootClass> worklist = new HashSet<>();
    for (ClassType tmp : allInterfaces) {
      SootClass msc = pta.getView().getClass(tmp).get();
      worklist.add(msc);
      while (msc.hasSuperclass()) {
        ClassType superType = msc.getSuperclass().get();
        SootClass superClazz = pta.getView().getClass(superType).get();
        if (!superClazz.isInterface()) break;
        worklist.add(superClazz);
        msc = superClazz;
      }
    }
    boolean flag = false;
    for (SootClass interf : worklist) {
      if (interf.getType()
          == pta.getView().getIdentifierFactory().getClassType("java.util.Collection")
      //    || interf.getType() == RefType.v("java.util.Map")
      ) {
        flag = true;
      }
    }
    return flag;
  }

  private boolean isNestedInClassImplementCollection(SootClass sc) {
    if (!sc.isInnerClass()) {
      return false;
    }
    ClassType outerType = sc.getOuterClass().get();
    SootClass outer = pta.getView().getClass(outerType).get();
    if (isImplementingCollection(outer)) {
      return true;
    }
    return isNestedInClassImplementCollection(outer);
  }

  private void computeContainerTypes() {
    for (Type type : t2Fields.keySet()) {
      if (type instanceof ClassType) {
        ClassType refType = (ClassType) type;
        SootClass sc = pta.getView().getClass(refType).get();
        if (isImplementingCollection(sc) || isNestedInClassImplementCollection(sc)) {
          containerType.add(type);
        } else {
          for (SparkField sf : t2Fields.get(type)) {
            if (sf.getType()
                == pta.getView().getIdentifierFactory().getClassType("java.lang.Object")) {
              containerType.add(type);
              break;
            }
          }
        }
      } else if (type instanceof ArrayType) {
        ArrayType at = (ArrayType) type;
        if (at.getBaseType()
            == pta.getView().getIdentifierFactory().getClassType("java.lang.Object")) {
          containerType.add(at);
        }
      } else {
        System.out.println(type);
      }
    }
    // build a mapping from any field type to their containing type.
    Map<Type, Set<Type>> ft2t = new HashMap<>();
    for (Type type : t2Fields.keySet()) {
      if (type instanceof ClassType) {
        for (SparkField sf : t2Fields.get(type)) {
          Type sft = sf.getType();
          if (sft instanceof ArrayType) {
            ArrayType at = (ArrayType) sft;
            sft = at.getBaseType();
          }
          ft2t.computeIfAbsent(sft, k -> new HashSet<>()).add(type);
        }
      } else if (type instanceof ArrayType) {
        ArrayType at = (ArrayType) type;
        ft2t.computeIfAbsent(at.getBaseType(), k -> new HashSet<>()).add(type);
      }
    }
    // find more container types by checking whether a type has a field of a container type.
    Set<Type> newlyFound = new HashSet<>();
    containerType.addAll(
        ft2t.getOrDefault(
            pta.getView().getIdentifierFactory().getClassType("java.lang.Object"),
            Collections.emptySet()));
    for (Type t1 : containerType) {
      for (Type t2 : ft2t.getOrDefault(t1, Collections.emptySet())) {
        if (!containerType.contains(t2)) {
          newlyFound.add(t2);
        }
      }
    }
    while (!newlyFound.isEmpty()) {
      containerType.addAll(newlyFound);
      Set<Type> tmp = new HashSet<>();
      for (Type t1 : newlyFound) {
        for (Type t2 : ft2t.getOrDefault(t1, Collections.emptySet())) {
          if (!containerType.contains(t2)) {
            tmp.add(t2);
          }
        }
      }
      newlyFound.clear();
      newlyFound.addAll(tmp);
    }
    System.out.println("#ContainerType:" + containerType.size());
  }

  private void computeContextDependentObjects() {
    for (AllocNode heap : pag.getAllocNodes()) {
      if (containerType.contains(heap.getType())) {
        ctxDepHeaps.add(heap);
      }
    }
    System.out.println("#OBJECTS:" + pag.getAllocNodes().size());
    System.out.println("#CS:" + ctxDepHeaps.size());
    System.out.println("#CI:" + (pag.getAllocNodes().size() - ctxDepHeaps.size()));
  }

  public void run() {
    buildHeapFieldsMapping();
    computeContainerTypes();
    computeContextDependentObjects();
  }
}
