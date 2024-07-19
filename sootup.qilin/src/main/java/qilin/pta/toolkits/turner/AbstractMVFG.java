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

package qilin.pta.toolkits.turner;

import java.util.*;
import qilin.core.PTA;
import qilin.core.PointsToAnalysis;
import qilin.core.builder.MethodNodeFactory;
import qilin.core.builder.callgraph.Edge;
import qilin.core.builder.callgraph.OnFlyCallGraph;
import qilin.core.pag.*;
import qilin.util.Pair;
import qilin.util.queue.QueueReader;
import qilin.util.queue.UniqueQueue;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.constant.NullConstant;
import sootup.core.jimple.common.expr.AbstractInstanceInvokeExpr;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.stmt.JAssignStmt;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.model.SootMethod;
import sootup.core.types.ReferenceType;

public abstract class AbstractMVFG {
  public static Map<SootMethod, AbstractMVFG> method2VFG = new HashMap<>();
  protected final PTA prePTA;
  protected final OCG hg;
  protected final SootMethod method;
  protected final Set<Object> sparkNodes = new HashSet<>();
  protected final Set<Object> csNodes = new HashSet<>();
  protected final Map<Object, Set<TranEdge>> outEdges = new HashMap<>();
  protected final Map<Object, Set<TranEdge>> inEdges = new HashMap<>();

  protected int total_edge_count = 0;

  public static AbstractMVFG findMethodVFG(SootMethod method) {
    return method2VFG.getOrDefault(method, null);
  }

  public AbstractMVFG(PTA prePTA, OCG hg, SootMethod method) {
    this.prePTA = prePTA;
    this.hg = hg;
    this.method = method;
  }

  public Collection<Object> getAllNodes() {
    return sparkNodes;
  }

  public int getTotalNodeCount() {
    return sparkNodes.size();
  }

  public int getTotalEdgeCount() {
    return total_edge_count;
  }

  public Collection<Object> getCSNodes() {
    return csNodes;
  }

  protected void addNormalEdge(TranEdge edge) {
    sparkNodes.add(edge.getSource());
    sparkNodes.add(edge.getTarget());
    total_edge_count++;
    outEdges.computeIfAbsent(edge.getSource(), k -> new HashSet<>()).add(edge);
    inEdges.computeIfAbsent(edge.getTarget(), k -> new HashSet<>()).add(edge);
  }

  protected void addNewEdge(AllocNode from, LocalVarNode to) {
    TranEdge newEdge = new TranEdge(from, to, DFA.TranCond.NEW);
    addNormalEdge(newEdge);
    TranEdge newInvEdge = new TranEdge(to, from, DFA.TranCond.INEW);
    addNormalEdge(newInvEdge);
  }

  protected void addCSLikelyEdge(AllocNode heap) {
    TranEdge csLikelyEdge = new TranEdge(heap, heap, DFA.TranCond.CSLIKELY);
    addNormalEdge(csLikelyEdge);
  }

  protected void addAssignEdge(LocalVarNode from, LocalVarNode to) {
    TranEdge assignEdge = new TranEdge(from, to, DFA.TranCond.ASSIGN);
    addNormalEdge(assignEdge);
    TranEdge assignInvEdge = new TranEdge(to, from, DFA.TranCond.IASSIGN);
    addNormalEdge(assignInvEdge);
  }

  protected void addStoreEdge(LocalVarNode from, LocalVarNode base) {
    TranEdge storeEdge = new TranEdge(from, base, DFA.TranCond.STORE);
    addNormalEdge(storeEdge);
    TranEdge storeInvEdge = new TranEdge(base, from, DFA.TranCond.ISTORE);
    addNormalEdge(storeInvEdge);
  }

  protected void addLoadEdge(LocalVarNode base, LocalVarNode to) {
    TranEdge loadEdge = new TranEdge(base, to, DFA.TranCond.LOAD);
    addNormalEdge(loadEdge);
    TranEdge loadInvEdge = new TranEdge(to, base, DFA.TranCond.ILOAD);
    addNormalEdge(loadInvEdge);
  }

  protected void buildVFG() {
    OnFlyCallGraph callGraph = prePTA.getCallGraph();
    PAG pag = prePTA.getPag();
    MethodPAG srcmpag = pag.getMethodPAG(method);
    MethodNodeFactory srcnf = srcmpag.nodeFactory();
    LocalVarNode thisRef = (LocalVarNode) srcnf.caseThis();
    QueueReader<Node> reader = srcmpag.getInternalReader().clone();
    while (reader.hasNext()) {
      Node from = reader.next(), to = reader.next();
      if (from instanceof LocalVarNode) {
        if (to instanceof LocalVarNode) this.addAssignEdge((LocalVarNode) from, (LocalVarNode) to);
        else if (to instanceof FieldRefNode) {
          FieldRefNode fr = (FieldRefNode) to;
          this.addStoreEdge((LocalVarNode) from, (LocalVarNode) fr.getBase());
        } // local-global

      } else if (from instanceof AllocNode) {
        if (to instanceof LocalVarNode) {
          this.addNewEdge((AllocNode) from, (LocalVarNode) to);
          if (hg.isCSLikely((AllocNode) from)) {
            this.addCSLikelyEdge((AllocNode) from);
          }
        } // GlobalVarNode
      } else if (from instanceof FieldRefNode) {
        FieldRefNode fr = (FieldRefNode) from;
        this.addLoadEdge((LocalVarNode) fr.getBase(), (LocalVarNode) to);
      } // global-local
    }
    // add exception edges that added dynamically during the pre-analysis.
    srcmpag
        .getExceptionEdges()
        .forEach(
            (k, vs) -> {
              for (Node v : vs) {
                this.addAssignEdge((LocalVarNode) k, (LocalVarNode) v);
              }
            });

    // add invoke edges
    for (final Stmt s : srcmpag.getInvokeStmts()) {
      AbstractInvokeExpr ie = s.getInvokeExpr();
      int numArgs = ie.getArgCount();
      Value[] args = new Value[numArgs];
      for (int i = 0; i < numArgs; i++) {
        Value arg = ie.getArg(i);
        if (!(arg.getType() instanceof ReferenceType) || arg instanceof NullConstant) continue;
        args[i] = arg;
      }
      LocalVarNode retDest = null;
      if (s instanceof JAssignStmt) {
        Value dest = ((JAssignStmt) s).getLeftOp();
        if (dest.getType() instanceof ReferenceType) {
          retDest = pag.findLocalVarNode(method, dest, dest.getType());
        }
      }
      LocalVarNode receiver;
      if (ie instanceof AbstractInstanceInvokeExpr) {
        AbstractInstanceInvokeExpr iie = (AbstractInstanceInvokeExpr) ie;
        Local base = iie.getBase();
        receiver = pag.findLocalVarNode(method, base, base.getType());
      } else {
        // static call
        receiver = thisRef;
      }

      Set<SootMethod> targets = new HashSet<>();
      for (Iterator<Edge> it = callGraph.edgesOutOf(s); it.hasNext(); ) {
        Edge e = it.next();
        SootMethod tgtmtd = e.tgt();
        targets.add(tgtmtd);
      }
      if (!targets.isEmpty()) {
        for (int i = 0; i < numArgs; i++) {
          if (args[i] == null) continue;
          ValNode argNode = pag.findValNode(args[i], method);
          if (argNode instanceof LocalVarNode && satisfyAddingStoreCondition(i, targets)) {
            this.addStoreEdge((LocalVarNode) argNode, receiver);
          }
        }
        if (retDest != null && retDest.getType() instanceof ReferenceType) {
          if (statisfyAddingLoadCondition(targets)) {
            this.addLoadEdge(receiver, retDest);
          }
        }
        if (statisfyAddingLoadCondition(targets)) {
          LocalVarNode stmtThrowNode = srcnf.makeInvokeStmtThrowVarNode(s, method);
          this.addLoadEdge(receiver, stmtThrowNode);
        }
        if (satisfyAddingStoreCondition(PointsToAnalysis.THIS_NODE, targets)) {
          this.addStoreEdge(receiver, receiver);
        }
      }
    }

    // add param and return edges
    addNormalEdge(new TranEdge(thisRef, thisRef, DFA.TranCond.PARAM));
    addNormalEdge(new TranEdge(thisRef, thisRef, DFA.TranCond.IPARAM));

    int numParms = method.getParameterCount();
    for (int i = 0; i < numParms; i++) {
      if (method.getParameterType(i) instanceof ReferenceType) {
        LocalVarNode param = (LocalVarNode) srcnf.caseParm(i);
        addNormalEdge(new TranEdge(param, param, DFA.TranCond.PARAM));
        addNormalEdge(new TranEdge(param, param, DFA.TranCond.IPARAM));
      }
    }
    if (method.getReturnType() instanceof ReferenceType) {
      LocalVarNode mret = (LocalVarNode) srcnf.caseRet();
      addStoreEdge(mret, thisRef);
    }
    LocalVarNode mThrow =
        pag.findLocalVarNode(
            method,
            new Parm(method, PointsToAnalysis.THROW_NODE),
            prePTA.getView().getIdentifierFactory().getClassType("java.lang.Exception"));
    if (mThrow != null) {
      addStoreEdge(mThrow, thisRef);
    }
  }

  protected abstract boolean statisfyAddingLoadCondition(Set<SootMethod> targets);

  protected abstract boolean satisfyAddingStoreCondition(int paramIndex, Set<SootMethod> targets);

  /*
   * Algorithm1: x \in R(flow) \cap R(iflow).
   * more efficient.
   * */
  public void computeNodesInPrecisionLossPatterns() {
    Queue<Pair<Object, DFA.State>> workList = new UniqueQueue<>();
    Map<DFA.State, Set<Object>> state2nodes = new HashMap<>();
    MethodPAG srcmpag = prePTA.getPag().getMethodPAG(method);
    MethodNodeFactory srcnf = srcmpag.nodeFactory();

    // initialize worklist
    int numParms = method.getParameterCount();
    LocalVarNode thisRef = (LocalVarNode) srcnf.caseThis();
    Set<Object> startState = state2nodes.computeIfAbsent(DFA.State.S, k -> new HashSet<>());
    startState.add(thisRef);
    workList.add(new Pair<>(thisRef, DFA.State.S));

    for (int i = 0; i < numParms; i++) {
      if (method.getParameterType(i) instanceof ReferenceType) {
        LocalVarNode param = (LocalVarNode) srcnf.caseParm(i);
        startState.add(param);
        workList.add(new Pair<>(param, DFA.State.S));
      }
    }
    if (method.getReturnType() instanceof ReferenceType) {
      LocalVarNode mret = (LocalVarNode) srcnf.caseRet();
      startState.add(mret);
      workList.add(new Pair<>(mret, DFA.State.S));
    }
    LocalVarNode mThrow = (LocalVarNode) srcnf.caseMethodThrow();
    startState.add(mThrow);
    workList.add(new Pair<>(mThrow, DFA.State.S));

    // propagate
    while (!workList.isEmpty()) {
      Pair<Object, DFA.State> pair = workList.poll();
      Object currNode = pair.getFirst();
      DFA.State currState = pair.getSecond();
      for (TranEdge e : outEdges.getOrDefault(currNode, Collections.emptySet())) {
        Object target = e.getTarget();
        DFA.TranCond tranCond = e.getTranCond();
        DFA.State nextState = DFA.nextState(currState, tranCond);
        if (nextState != DFA.State.ERROR) {
          Set<Object> stateNodes = state2nodes.computeIfAbsent(nextState, k -> new HashSet<>());
          if (stateNodes.add(target)) {
            workList.add(new Pair<>(target, nextState));
          }
        }
      }
    }
    // collect cs nodes.
    Set<Object> flowNodes = state2nodes.getOrDefault(DFA.State.FLOW, Collections.emptySet());
    Set<Object> iflowNodes = state2nodes.getOrDefault(DFA.State.IFLOW, Collections.emptySet());
    for (Object sparkNode : sparkNodes) {
      if (flowNodes.contains(sparkNode) && iflowNodes.contains(sparkNode)) {
        csNodes.add(sparkNode);
      }
    }
  }
}
