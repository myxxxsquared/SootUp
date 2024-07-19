/* Qilin - a Java Pointer Analysis Framework
 * Copyright (C) 2021-2030 Qilin developers
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3.0 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <https://www.gnu.org/licenses/lgpl-3.0.en.html>.
 */

package qilin.core.pag;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import qilin.CoreConfig;
import qilin.core.PTA;
import qilin.core.PointsToAnalysis;
import qilin.core.builder.CallGraphBuilder;
import qilin.core.context.Context;
import qilin.core.natives.NativeMethodDriver;
import qilin.core.reflection.NopReflectionModel;
import qilin.core.reflection.ReflectionModel;
import qilin.core.reflection.TamiflexModel;
import qilin.util.ArrayNumberer;
import qilin.util.DataFactory;
import qilin.util.PTAUtils;
import qilin.util.Triple;
import qilin.util.queue.ChunkedQueue;
import qilin.util.queue.QueueReader;
import sootup.core.graph.MutableStmtGraph;
import sootup.core.jimple.Jimple;
import sootup.core.jimple.basic.LValue;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.StmtPositionInfo;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.constant.ClassConstant;
import sootup.core.jimple.common.constant.IntConstant;
import sootup.core.jimple.common.constant.StringConstant;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.expr.JStaticInvokeExpr;
import sootup.core.jimple.common.stmt.JAssignStmt;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.model.Body;
import sootup.core.model.SootClass;
import sootup.core.model.SootMethod;
import sootup.core.signatures.FieldSignature;
import sootup.core.types.ArrayType;
import sootup.core.types.ClassType;
import sootup.core.types.Type;
import sootup.core.views.View;
import sootup.java.core.JavaIdentifierFactory;
import sootup.java.core.language.JavaJimple;

/**
 * Pointer assignment graph.
 *
 * @author Ondrej Lhotak
 */
public class PAG {
  protected final NativeMethodDriver nativeDriver;
  protected final ReflectionModel reflectionModel;

  // ========================= context-sensitive nodes =================================
  protected final Map<VarNode, Map<Context, ContextVarNode>> contextVarNodeMap;
  protected final Map<AllocNode, Map<Context, ContextAllocNode>> contextAllocNodeMap;
  protected final Map<SootMethod, Map<Context, ContextMethod>> contextMethodMap;
  protected final Map<MethodPAG, Set<Context>> addedContexts;
  protected final Map<Context, Map<SparkField, ContextField>> contextFieldMap;

  // ==========================data=========================
  protected ArrayNumberer<AllocNode> allocNodeNumberer = new ArrayNumberer<>();
  protected ArrayNumberer<ValNode> valNodeNumberer = new ArrayNumberer<>();
  protected ArrayNumberer<FieldRefNode> fieldRefNodeNumberer = new ArrayNumberer<>();
  private static AtomicInteger maxFinishNumber = new AtomicInteger(0);

  // ========================= ir to Node ==============================================
  protected final Map<Object, AllocNode> valToAllocNode;
  protected final Map<Object, ValNode> valToValNode;
  protected final Map<SootMethod, MethodPAG> methodToPag;
  protected final Set<FieldSignature> globals;
  protected final Set<Triple<SootMethod, Local, Type>> locals;
  // ==========================outer objects==============================
  protected ChunkedQueue<Node> edgeQueue;

  protected final Map<ValNode, Set<ValNode>> simple;
  protected final Map<ValNode, Set<ValNode>> simpleInv;
  protected final Map<FieldRefNode, Set<VarNode>> load;
  protected final Map<VarNode, Set<FieldRefNode>> loadInv;
  protected final Map<AllocNode, Set<VarNode>> alloc;
  protected final Map<VarNode, Set<AllocNode>> allocInv;
  protected final Map<VarNode, Set<FieldRefNode>> store;
  protected final Map<FieldRefNode, Set<VarNode>> storeInv;

  protected final PTA pta;

  public PAG(PTA pta) {
    this.pta = pta;
    this.simple = DataFactory.createMap();
    this.simpleInv = DataFactory.createMap();
    this.load = DataFactory.createMap();
    this.loadInv = DataFactory.createMap();
    this.alloc = DataFactory.createMap();
    this.allocInv = DataFactory.createMap();
    this.store = DataFactory.createMap();
    this.storeInv = DataFactory.createMap();
    this.nativeDriver = new NativeMethodDriver(pta.getScene());
    this.reflectionModel = createReflectionModel();
    this.contextVarNodeMap = DataFactory.createMap(16000);
    this.contextAllocNodeMap = DataFactory.createMap(6000);
    this.contextMethodMap = DataFactory.createMap(6000);
    this.addedContexts = DataFactory.createMap();
    this.contextFieldMap = DataFactory.createMap(6000);
    this.valToAllocNode = DataFactory.createMap(10000);
    this.valToValNode = DataFactory.createMap(100000);
    this.methodToPag = DataFactory.createMap();
    this.globals = DataFactory.createSet(100000);
    this.locals = DataFactory.createSet(100000);
  }

  public void setEdgeQueue(ChunkedQueue<Node> edgeQueue) {
    this.edgeQueue = edgeQueue;
  }

  public Map<AllocNode, Set<VarNode>> getAlloc() {
    return alloc;
  }

  public Map<ValNode, Set<ValNode>> getSimple() {
    return simple;
  }

  public Map<ValNode, Set<ValNode>> getSimpleInv() {
    return simpleInv;
  }

  public Map<FieldRefNode, Set<VarNode>> getLoad() {
    return load;
  }

  public Map<FieldRefNode, Set<VarNode>> getStoreInv() {
    return storeInv;
  }

  public PTA getPta() {
    return this.pta;
  }

  public CallGraphBuilder getCgb() {
    return pta.getCgb();
  }

  public QueueReader<Node> edgeReader() {
    return edgeQueue.reader();
  }

  // =======================add edge===============================
  protected <K, V> boolean addToMap(Map<K, Set<V>> m, K key, V value) {
    Set<V> valueList = m.computeIfAbsent(key, k -> DataFactory.createSet(4));
    return valueList.add(value);
  }

  private boolean addAllocEdge(AllocNode from, VarNode to) {
    if (addToMap(alloc, from, to)) {
      addToMap(allocInv, to, from);
      return true;
    }
    return false;
  }

  private boolean addSimpleEdge(ValNode from, ValNode to) {
    if (addToMap(simple, from, to)) {
      addToMap(simpleInv, to, from);
      return true;
    }
    return false;
  }

  private boolean addStoreEdge(VarNode from, FieldRefNode to) {
    if (addToMap(storeInv, to, from)) {
      addToMap(store, from, to);
      return true;
    }
    return false;
  }

  private boolean addLoadEdge(FieldRefNode from, VarNode to) {
    if (addToMap(load, from, to)) {
      addToMap(loadInv, to, from);
      return true;
    }
    return false;
  }

  public void addGlobalPAGEdge(Node from, Node to) {
    from = pta.parameterize(from, pta.emptyContext());
    to = pta.parameterize(to, pta.emptyContext());
    addEdge(from, to);
  }

  /** Adds an edge to the graph, returning false if it was already there. */
  public final void addEdge(Node from, Node to) {
    if (addEdgeIntenal(from, to)) {
      edgeQueue.add(from);
      edgeQueue.add(to);
    }
  }

  private boolean addEdgeIntenal(Node from, Node to) {
    if (from instanceof ValNode) {
      if (to instanceof ValNode) {
        return addSimpleEdge((ValNode) from, (ValNode) to);
      } else {
        return addStoreEdge((VarNode) from, (FieldRefNode) to);
      }
    } else if (from instanceof FieldRefNode) {
      return addLoadEdge((FieldRefNode) from, (VarNode) to);
    } else {
      AllocNode heap = (AllocNode) from;
      return addAllocEdge(heap, (VarNode) to);
    }
  }

  // ======================lookups===========================
  protected <K, V> Set<V> lookup(Map<K, Set<V>> m, K key) {
    return m.getOrDefault(key, Collections.emptySet());
  }

  public Set<VarNode> allocLookup(AllocNode key) {
    return lookup(alloc, key);
  }

  public Set<AllocNode> allocInvLookup(VarNode key) {
    return lookup(allocInv, key);
  }

  public Set<ValNode> simpleLookup(ValNode key) {
    return lookup(simple, key);
  }

  public Set<ValNode> simpleInvLookup(ValNode key) {
    return lookup(simpleInv, key);
  }

  public Set<FieldRefNode> loadInvLookup(VarNode key) {
    return lookup(loadInv, key);
  }

  public Set<VarNode> loadLookup(FieldRefNode key) {
    return lookup(load, key);
  }

  public Set<FieldRefNode> storeLookup(VarNode key) {
    return lookup(store, key);
  }

  public Set<VarNode> storeInvLookup(FieldRefNode key) {
    return lookup(storeInv, key);
  }

  public static int nextFinishNumber() {
    return maxFinishNumber.incrementAndGet();
  }

  public ArrayNumberer<AllocNode> getAllocNodeNumberer() {
    return allocNodeNumberer;
  }

  public ArrayNumberer<FieldRefNode> getFieldRefNodeNumberer() {
    return fieldRefNodeNumberer;
  }

  public ArrayNumberer<ValNode> getValNodeNumberer() {
    return valNodeNumberer;
  }

  public Collection<ValNode> getValNodes() {
    return valToValNode.values();
  }

  public Collection<AllocNode> getAllocNodes() {
    return valToAllocNode.values();
  }

  public Set<FieldSignature> getGlobalPointers() {
    return globals;
  }

  public Set<Triple<SootMethod, Local, Type>> getLocalPointers() {
    return locals;
  }

  /** Finds the ValNode for the variable value, or returns null. */
  public ValNode findValNode(Object value, SootMethod containingMethod) {
    if (value instanceof Local) {
      Local local = (Local) value;
      Triple<SootMethod, Object, Type> localTriple =
          new Triple<>(containingMethod, local, local.getType());
      return valToValNode.get(localTriple);
    } else {
      return valToValNode.get(value);
    }
  }

  public AllocNode findAllocNode(Object obj) {
    return valToAllocNode.get(obj);
  }

  // ==========================create nodes==================================
  public AllocNode makeAllocNode(Object newExpr, Type type, SootMethod m) {
    if (type instanceof ClassType) {
      ClassType rt = (ClassType) type;
      View view = pta.getView();
      Optional<? extends SootClass> osc = view.getClass(rt);
      if (osc.isPresent() && osc.get().isAbstract()) {
        boolean usesReflectionLog = CoreConfig.v().getAppConfig().REFLECTION_LOG != null;
        if (!usesReflectionLog) {
          throw new RuntimeException("Attempt to create allocnode with abstract type " + rt);
        }
      }
    }
    AllocNode ret = valToAllocNode.get(newExpr);
    if (ret == null) {
      valToAllocNode.put(newExpr, ret = new AllocNode(newExpr, type, m));
      allocNodeNumberer.add(ret);
    } else if (!(ret.getType().equals(type))) {
      throw new RuntimeException(
          "NewExpr " + newExpr + " of type " + type + " previously had type " + ret.getType());
    }
    return ret;
  }

  public AllocNode makeStringConstantNode(StringConstant sc) {
    StringConstant stringConstant = sc;
    if (!CoreConfig.v().getPtaConfig().stringConstants) {
      stringConstant = JavaJimple.getInstance().newStringConstant(PointsToAnalysis.STRING_NODE);
    }
    AllocNode ret = valToAllocNode.get(stringConstant);
    if (ret == null) {
      valToAllocNode.put(stringConstant, ret = new StringConstantNode(stringConstant));
      allocNodeNumberer.add(ret);
    }
    return ret;
  }

  public AllocNode makeClassConstantNode(ClassConstant cc) {
    AllocNode ret = valToAllocNode.get(cc);
    if (ret == null) {
      valToAllocNode.put(cc, ret = new ClassConstantNode(cc));
      allocNodeNumberer.add(ret);
    }
    return ret;
  }

  /** Finds or creates the GlobalVarNode for the variable value, of type type. */
  public GlobalVarNode makeGlobalVarNode(Object value, Type type) {
    // value could only be a StringConstant, ClassConstant, or SootField.
    GlobalVarNode ret = (GlobalVarNode) valToValNode.get(value);
    if (ret == null) {
      ret =
          (GlobalVarNode) valToValNode.computeIfAbsent(value, k -> new GlobalVarNode(value, type));
      valNodeNumberer.add(ret);
      if (value instanceof FieldSignature) {
        globals.add((FieldSignature) value);
      }
    } else if (!(ret.getType().equals(type))) {
      throw new RuntimeException(
          "Value " + value + " of type " + type + " previously had type " + ret.getType());
    }
    return ret;
  }

  /** Finds or creates the LocalVarNode for the variable value, of type type. */
  public LocalVarNode makeLocalVarNode(Object value, Type type, SootMethod method) {
    Triple<SootMethod, Object, Type> localTriple = new Triple<>(method, value, type);
    LocalVarNode ret = (LocalVarNode) valToValNode.get(localTriple);
    if (ret == null) {
      valToValNode.put(localTriple, ret = new LocalVarNode(value, type, method));
      valNodeNumberer.add(ret);
      if (value instanceof Local) {
        Local local = (Local) value;
        locals.add(new Triple<>(method, local, type));
      }
    } else if (!(ret.getType().equals(type))) {
      throw new RuntimeException(
          "Value " + value + " of type " + type + " previously had type " + ret.getType());
    }
    return ret;
  }

  /**
   * Finds or creates the FieldVarNode for the Java field or array element. Treat Java field and
   * array element as normal local variable.
   */
  public FieldValNode makeFieldValNode(SparkField field) {
    FieldValNode ret = (FieldValNode) valToValNode.get(field);
    if (ret == null) {
      valToValNode.put(field, ret = new FieldValNode(field));
      valNodeNumberer.add(ret);
    }
    return ret;
  }

  /** Finds or creates the FieldRefNode for base variable base and field field, of type type. */
  public FieldRefNode makeFieldRefNode(VarNode base, SparkField field) {
    FieldRefNode ret = base.dot(field);
    if (ret == null) {
      ret = new FieldRefNode(base, field);
      fieldRefNodeNumberer.add(ret);
    }
    return ret;
  }

  /** Finds or creates the ContextVarNode for base variable base and context. */
  public ContextVarNode makeContextVarNode(VarNode base, Context context) {
    Map<Context, ContextVarNode> contextMap =
        contextVarNodeMap.computeIfAbsent(base, k1 -> DataFactory.createMap());
    ContextVarNode ret = contextMap.get(context);
    if (ret == null) {
      contextMap.put(context, ret = new ContextVarNode(base, context));
      valNodeNumberer.add(ret);
    }
    return ret;
  }

  /** Finds or creates the ContextAllocNode for base alloc site and context. */
  public ContextAllocNode makeContextAllocNode(AllocNode allocNode, Context context) {
    Map<Context, ContextAllocNode> contextMap =
        contextAllocNodeMap.computeIfAbsent(allocNode, k1 -> DataFactory.createMap());
    ContextAllocNode ret = contextMap.get(context);
    if (ret == null) {
      contextMap.put(context, ret = new ContextAllocNode(allocNode, context));
      allocNodeNumberer.add(ret);
    }
    return ret;
  }

  /** Finds or creates the ContextMethod for method and context. */
  public ContextMethod makeContextMethod(Context context, SootMethod method) {
    Map<Context, ContextMethod> contextMap =
        contextMethodMap.computeIfAbsent(method, k1 -> DataFactory.createMap());
    return contextMap.computeIfAbsent(context, k -> new ContextMethod(method, context));
  }

  public AllocNode getAllocNode(Object val) {
    return valToAllocNode.get(val);
  }

  public Map<MethodPAG, Set<Context>> getMethod2ContextsMap() {
    return addedContexts;
  }

  public Collection<ContextField> getContextFields() {
    return contextFieldMap.values().stream()
        .flatMap(m -> m.values().stream())
        .collect(Collectors.toSet());
  }

  public Map<VarNode, Map<Context, ContextVarNode>> getContextVarNodeMap() {
    return contextVarNodeMap;
  }

  public Map<AllocNode, Map<Context, ContextAllocNode>> getContextAllocNodeMap() {
    return contextAllocNodeMap;
  }

  public Map<SootMethod, Map<Context, ContextMethod>> getContextMethodMap() {
    return contextMethodMap;
  }

  public Map<Context, Map<SparkField, ContextField>> getContextFieldVarNodeMap() {
    return contextFieldMap;
  }

  public ContextField makeContextField(Context context, FieldValNode fieldValNode) {
    SparkField field = fieldValNode.getField();
    Map<SparkField, ContextField> field2odotf =
        contextFieldMap.computeIfAbsent(context, k -> DataFactory.createMap());
    ContextField ret = field2odotf.get(field);
    if (ret == null) {
      field2odotf.put(field, ret = new ContextField(context, field));
      valNodeNumberer.add(ret);
    }
    return ret;
  }

  public Collection<VarNode> getVarNodes(SootMethod m, Local local) {
    LocalVarNode lvn = findLocalVarNode(m, local, local.getType());
    Map<?, ContextVarNode> subMap = contextVarNodeMap.get(lvn);
    if (subMap == null) {
      return Collections.emptySet();
    }
    return new HashSet<>(subMap.values());
  }

  // ===================find nodes==============================

  /** Finds the GlobalVarNode for the variable value, or returns null. */
  public GlobalVarNode findGlobalVarNode(Object value) {
    if (value instanceof Local) {
      System.out.println("Warning: find global varnode for local value:" + value);
      return null;
    } else {
      return (GlobalVarNode) valToValNode.get(value);
    }
  }

  /** Finds the LocalVarNode for the variable value, or returns null. */
  public LocalVarNode findLocalVarNode(SootMethod m, Object value, Type type) {
    Triple<SootMethod, Object, Type> key = new Triple<>(m, value, type);
    ValNode ret = valToValNode.get(key);
    if (ret instanceof LocalVarNode) {
      return (LocalVarNode) ret;
    }
    return null;
  }

  /** Finds the ContextVarNode for base variable value and context context, or returns null. */
  public ContextVarNode findContextVarNode(SootMethod m, Local baseValue, Context context) {
    LocalVarNode lvn = findLocalVarNode(m, baseValue, baseValue.getType());
    Map<Context, ContextVarNode> contextMap = contextVarNodeMap.get(lvn);
    return contextMap == null ? null : contextMap.get(context);
  }

  protected ReflectionModel createReflectionModel() {
    ReflectionModel model;
    if (CoreConfig.v().getAppConfig().REFLECTION_LOG != null
        && CoreConfig.v().getAppConfig().REFLECTION_LOG.length() > 0) {
      model = new TamiflexModel(pta.getScene());
    } else {
      model = new NopReflectionModel(pta.getScene());
    }
    return model;
  }

  public MethodPAG getMethodPAG(SootMethod m) {
    if (methodToPag.containsKey(m)) {
      return methodToPag.get(m);
    }
    if (m.isConcrete()) {
      reflectionModel.buildReflection(m);
    }
    if (m.isNative()) {
      nativeDriver.buildNative(m);
    } else {
      // we will revert these back in the future.
      /*
       * To keep same with Doop, we move the simulation of
       * <java.lang.System: void arraycopy(java.lang.Object,int,java.lang.Object,int,int)>
       * directly to its caller methods.
       * */
      if (pta.getScene().arraycopyBuilt.add(m)) {
        handleArrayCopy(m);
      }
    }
    Body body = PTAUtils.getMethodBody(m);
    return methodToPag.computeIfAbsent(m, k -> new MethodPAG(this, m, body));
  }

  private void handleArrayCopy(SootMethod method) {
    Map<Stmt, Collection<JAssignStmt>> newUnits = DataFactory.createMap();
    Body body = PTAUtils.getMethodBody(method);
    Body.BodyBuilder builder = Body.builder(body, Collections.emptySet());
    int localCount = body.getLocalCount();
    for (Stmt s : body.getStmts()) {
      if (s.containsInvokeExpr()) {
        AbstractInvokeExpr invokeExpr = s.getInvokeExpr();
        if (invokeExpr instanceof JStaticInvokeExpr) {
          JStaticInvokeExpr sie = (JStaticInvokeExpr) invokeExpr;
          String sig = sie.getMethodSignature().toString();
          if (sig.equals(
              "<java.lang.System: void arraycopy(java.lang.Object,int,java.lang.Object,int,int)>")) {
            Value srcArr = sie.getArg(0);
            if (PTAUtils.isPrimitiveArrayType(srcArr.getType())) {
              continue;
            }
            Type objType = JavaIdentifierFactory.getInstance().getClassType("java.lang.Object");
            if (srcArr.getType() == objType) {
              Local localSrc =
                  Jimple.newLocal("intermediate/" + (localCount++), new ArrayType(objType, 1));
              builder.addLocal(localSrc);
              newUnits
                  .computeIfAbsent(s, k -> new HashSet<>())
                  .add(new JAssignStmt(localSrc, srcArr, StmtPositionInfo.getNoStmtPositionInfo()));
              srcArr = localSrc;
            }
            Value dstArr = sie.getArg(2);
            if (PTAUtils.isPrimitiveArrayType(dstArr.getType())) {
              continue;
            }
            if (dstArr.getType() == objType) {
              Local localDst =
                  Jimple.newLocal("intermediate/" + (localCount++), new ArrayType(objType, 1));
              builder.addLocal(localDst);
              newUnits
                  .computeIfAbsent(s, k -> new HashSet<>())
                  .add(new JAssignStmt(localDst, dstArr, StmtPositionInfo.getNoStmtPositionInfo()));
              dstArr = localDst;
            }
            Value src =
                JavaJimple.getInstance().newArrayRef((Local) srcArr, IntConstant.getInstance(0));
            LValue dst =
                JavaJimple.getInstance().newArrayRef((Local) dstArr, IntConstant.getInstance(0));
            Local local =
                Jimple.newLocal(
                    "nativeArrayCopy" + (localCount++),
                    JavaIdentifierFactory.getInstance().getClassType("java.lang.Object"));
            builder.addLocal(local);
            newUnits
                .computeIfAbsent(s, k -> DataFactory.createSet())
                .add(new JAssignStmt(local, src, StmtPositionInfo.getNoStmtPositionInfo()));
            newUnits
                .computeIfAbsent(s, k -> DataFactory.createSet())
                .add(new JAssignStmt(dst, local, StmtPositionInfo.getNoStmtPositionInfo()));
          }
        }
      }
    }

    final MutableStmtGraph stmtGraph = builder.getStmtGraph();
    for (Stmt unit : newUnits.keySet()) {
      for (JAssignStmt succ : newUnits.get(unit)) {
        stmtGraph.insertBefore(unit, succ);
      }
    }
    PTAUtils.updateMethodBody(method, builder.build());
  }

  public void resetPointsToSet() {
    this.addedContexts.clear();
    contextVarNodeMap.values().stream()
        .flatMap(m -> m.values().stream())
        .forEach(ValNode::discardP2Set);
    contextFieldMap.values().stream()
        .flatMap(m -> m.values().stream())
        .forEach(ValNode::discardP2Set);
    valToValNode.values().forEach(ValNode::discardP2Set);
    addedContexts.clear();
  }
}
