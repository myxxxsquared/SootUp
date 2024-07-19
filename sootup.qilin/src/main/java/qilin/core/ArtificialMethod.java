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

package qilin.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import sootup.core.jimple.Jimple;
import sootup.core.jimple.basic.*;
import sootup.core.jimple.common.constant.IntConstant;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.expr.JNewExpr;
import sootup.core.jimple.common.ref.*;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.model.Body;
import sootup.core.model.SootClass;
import sootup.core.model.SootField;
import sootup.core.model.SootMethod;
import sootup.core.signatures.MethodSignature;
import sootup.core.types.ArrayType;
import sootup.core.types.ClassType;
import sootup.core.types.Type;
import sootup.java.core.language.JavaJimple;
import sootup.java.core.views.JavaView;

public abstract class ArtificialMethod {
  protected final JavaView view;
  protected SootMethod method;
  protected Body.BodyBuilder bodyBuilder;
  protected Local thisLocal;
  protected Local[] paraLocals;
  protected int paraStart;
  protected int localStart;
  protected final List<Stmt> stmtList;

  protected ArtificialMethod(JavaView view) {
    this.view = view;
    this.stmtList = new ArrayList<>();
  }

  protected Local getThis() {
    if (thisLocal == null) {
      ClassType type = method.getDeclaringClassType();
      IdentityRef thisRef = new JThisRef(type);
      thisLocal = getLocal(type, 0);
      addIdentity(thisLocal, thisRef);
    }
    return thisLocal;
  }

  protected Local getPara(int index) {
    Local paraLocal = paraLocals[index];
    if (paraLocal == null) {
      Type type = method.getParameterType(index);
      return getPara(index, type);
    }
    return paraLocal;
  }

  protected Local getPara(int index, Type type) {
    Local paraLocal = paraLocals[index];
    if (paraLocal == null) {
      IdentityRef paraRef = new JParameterRef(type, index);
      paraLocal = getLocal(type, paraStart + index);
      addIdentity(paraLocal, paraRef);
      paraLocals[index] = paraLocal;
    }
    return paraLocal;
  }

  private void addIdentity(Local lValue, IdentityRef rValue) {
    Stmt identityStmt =
        Jimple.newIdentityStmt(lValue, rValue, StmtPositionInfo.getNoStmtPositionInfo());
    stmtList.add(identityStmt);
  }

  protected Local getNew(ClassType type) {
    Value newExpr = new JNewExpr(type);
    Local local = getNextLocal(type);
    addAssign(local, newExpr);
    return local;
  }

  protected Local getNewArray(ClassType type) {
    Value newExpr = JavaJimple.getInstance().newNewArrayExpr(type, IntConstant.getInstance(1));
    Local local = getNextLocal(new ArrayType(type, 1));
    addAssign(local, newExpr);
    return local;
  }

  protected Local getNextLocal(Type type) {
    return getLocal(type, localStart++);
  }

  private Local getLocal(Type type, int index) {
    Local local = Jimple.newLocal("r" + index, type);
    bodyBuilder.addLocal(local);
    return local;
  }

  protected void addReturn(Immediate ret) {
    Stmt stmt = Jimple.newReturnStmt(ret, StmtPositionInfo.getNoStmtPositionInfo());
    stmtList.add(stmt);
  }

  protected JStaticFieldRef getStaticFieldRef(String className, String name) {
    // TODO: build signature directly without class definition retreival
    SootClass sc = view.getClass(view.getIdentifierFactory().getClassType(className)).get();
    SootField field = sc.getField(name).get();
    return Jimple.newStaticFieldRef(field.getSignature());
  }

  protected JArrayRef getArrayRef(Value base) {
    return JavaJimple.getInstance().newArrayRef((Local) base, IntConstant.getInstance(0));
  }

  /** add an instance invocation receiver.sig(args) */
  protected void addInvoke(Local receiver, MethodSignature methodSig, Immediate... args) {
    SootMethod method = (SootMethod) view.getMethod(methodSig).get();
    SootClass clazz = (SootClass) view.getClass(method.getDeclaringClassType()).get();
    List<Immediate> argsL = Arrays.asList(args);
    AbstractInvokeExpr invoke =
        clazz.isInterface()
            ? Jimple.newInterfaceInvokeExpr(receiver, methodSig, argsL)
            : Jimple.newVirtualInvokeExpr(receiver, methodSig, argsL);
    Stmt stmt = Jimple.newInvokeStmt(invoke, StmtPositionInfo.getNoStmtPositionInfo());
    stmtList.add(stmt);
  }

  /**
   * add an instance invocation and get the return value rx = receiver.sig(args)
   *
   * @return rx
   */
  protected Local getInvoke(Local receiver, MethodSignature methodSig, Immediate... args) {
    SootMethod method = view.getMethod(methodSig).get();
    SootClass clazz = view.getClass(method.getDeclaringClassType()).get();
    List<Immediate> argsL = Arrays.asList(args);
    Value invoke =
        clazz.isInterface()
            ? Jimple.newInterfaceInvokeExpr(receiver, methodSig, argsL)
            : Jimple.newVirtualInvokeExpr(receiver, methodSig, argsL);
    Local rx = getNextLocal(method.getReturnType());
    addAssign(rx, invoke);
    return rx;
  }

  /** add a static invocation sig(args) */
  protected void addInvoke(String sig, Immediate... args) {
    MethodSignature methodSig = view.getIdentifierFactory().parseMethodSignature(sig);
    List<Immediate> argsL = Arrays.asList(args);
    Stmt stmt =
        Jimple.newInvokeStmt(
            Jimple.newStaticInvokeExpr(methodSig, argsL), StmtPositionInfo.getNoStmtPositionInfo());
    stmtList.add(stmt);
  }

  /**
   * add a static invocation and get the return value rx = sig(args)
   *
   * @return rx
   */
  protected Value getInvoke(String sig, Immediate... args) {
    MethodSignature methodSig = view.getIdentifierFactory().parseMethodSignature(sig);
    List<Immediate> argsL = Arrays.asList(args);
    LValue rx = getNextLocal(methodSig.getType());
    addAssign(rx, Jimple.newStaticInvokeExpr(methodSig, argsL));
    return rx;
  }

  protected void addAssign(LValue lValue, Value rValue) {
    Stmt stmt = Jimple.newAssignStmt(lValue, rValue, StmtPositionInfo.getNoStmtPositionInfo());
    stmtList.add(stmt);
  }
}
