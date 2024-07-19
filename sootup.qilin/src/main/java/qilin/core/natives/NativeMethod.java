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

import java.util.Collections;
import qilin.core.ArtificialMethod;
import qilin.util.PTAUtils;
import sootup.core.graph.MutableStmtGraph;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.model.Body;
import sootup.core.model.SootMethod;
import sootup.java.core.views.JavaView;

public abstract class NativeMethod extends ArtificialMethod {
  NativeMethod(JavaView view, SootMethod method) {
    super(view);
    this.method = method;
    Body body = PTAUtils.getMethodBody(method);
    this.bodyBuilder = Body.builder(body, Collections.emptySet());
    int paraCount = method.getParameterCount();
    paraLocals = new Local[paraCount];
    this.paraStart = method.isStatic() ? 0 : 1;
    this.localStart = this.paraStart + paraCount;
  }

  protected abstract void simulateImpl();

  public void simulate() {
    simulateImpl();
    MutableStmtGraph stmtGraph = bodyBuilder.getStmtGraph();
    stmtGraph.addBlock(stmtList);
    Stmt curr = stmtList.get(0);
    stmtGraph.setStartingStmt(curr);
    PTAUtils.updateMethodBody(method, bodyBuilder.build());
  }
}
