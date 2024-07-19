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

import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.StmtPositionInfo;
import sootup.core.jimple.common.ref.JStaticFieldRef;
import sootup.core.jimple.common.stmt.JReturnVoidStmt;
import sootup.core.model.SootMethod;
import sootup.java.core.views.JavaView;

public class JavaLangSystemSetOut0Native extends NativeMethod {
  public JavaLangSystemSetOut0Native(JavaView view, SootMethod method) {
    super(view, method);
  }

  /**
   * NOTE: this native method is not documented in JDK API. It should have the side effect:
   * System.out = parameter
   *
   * <p>private static native void setOut0(java.io.PrintStream);
   */
  protected void simulateImpl() {
    Local r1 = getPara(0);
    JStaticFieldRef systemOut = getStaticFieldRef("java.lang.System", "out");
    addAssign(systemOut, r1);
    final JReturnVoidStmt returnStmt =
        new JReturnVoidStmt(StmtPositionInfo.getNoStmtPositionInfo());
    stmtList.add(returnStmt);
  }
}
