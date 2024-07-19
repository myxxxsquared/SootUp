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

package qilin.core.natives;

import sootup.core.jimple.basic.LValue;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.StmtPositionInfo;
import sootup.core.jimple.common.stmt.JReturnVoidStmt;
import sootup.core.model.SootMethod;
import sootup.java.core.views.JavaView;

public class JavaLangThreadStart0Native extends NativeMethod {
  private final LValue currentThread;

  public JavaLangThreadStart0Native(JavaView view, SootMethod method, LValue currentThread) {
    super(view, method);
    this.currentThread = currentThread;
  }

  /**
   * Calls to Thread.start() get redirected to Thread.run.
   *
   * <p>In JRE 1.5 and JRE 1.6 Thread.start() is defined in Java and there is native method start0.
   */
  @Override
  protected void simulateImpl() {
    Local mThis = getThis();
    addInvoke(
        mThis, view.getIdentifierFactory().parseMethodSignature("<java.lang.Thread: void run()>"));
    addAssign(currentThread, mThis); // store.
    final JReturnVoidStmt returnStmt =
        new JReturnVoidStmt(StmtPositionInfo.getNoStmtPositionInfo());
    stmtList.add(returnStmt);
  }
}
