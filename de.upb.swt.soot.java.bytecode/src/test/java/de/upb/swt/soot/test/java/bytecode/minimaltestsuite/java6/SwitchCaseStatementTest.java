package de.upb.swt.soot.test.java.bytecode.minimaltestsuite.java6;

import categories.Java8Test;
import de.upb.swt.soot.core.model.SootMethod;
import de.upb.swt.soot.core.signatures.MethodSignature;
import de.upb.swt.soot.test.java.bytecode.minimaltestsuite.MinimalBytecodeTestSuiteBase;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** @author Kaustubh Kelkar */
@Category(Java8Test.class)
public class SwitchCaseStatementTest extends MinimalBytecodeTestSuiteBase {

  @Test
  public void testEnum() {
    SootMethod method = loadMethod(getMethodSignature("switchCaseStatementEnum"));
    assertJimpleStmts(
        method,
        expectedBodyStmts(
            "l0 := @this: SwitchCaseStatement",
            "l1 = \"RED\"",
            "l2 = \"\"",
            "$stack3 = <SwitchCaseStatement$1: int[] $SwitchMap$SwitchCaseStatement$Color>",
            "$stack4 = staticinvoke <SwitchCaseStatement$Color: SwitchCaseStatement$Color valueOf(java.lang.String)>(l1)",
            "$stack5 = virtualinvoke $stack4.<SwitchCaseStatement$Color: int ordinal()>()",
            "$stack6 = $stack3[$stack5]",
            "switch($stack6)",
            "case 1: goto label1",
            "case 2: goto label2",
            "default: goto label3",
            "label1:",
            "l2 = \"color red detected\"",
            "goto label4",
            "label2:",
            "l2 = \"color green detected\"",
            "goto label4",
            "label3:",
            "l2 = \"invalid color\"",
            "label4:",
            "return"));
  }

  @Test
  public void testSwitchInt() {
    SootMethod method = loadMethod(getMethodSignature("switchCaseStatementInt"));
    assertJimpleStmts(
        method,
        expectedBodyStmts(
            "l0 := @this: SwitchCaseStatement",
            "l1 = 2",
            "switch(l1)",
            "case 1: goto label1",
            "case 2: goto label2",
            "case 3: goto label3",
            "default: goto label4",
            "label1:",
            "l2 = \"number 1 detected\"",
            "goto label5",
            "label2:",
            "l2 = \"number 2 detected\"",
            "goto label5",
            "label3:",
            "l2 = \"number 3 detected\"",
            "goto label5",
            "label4:",
            "l2 = \"invalid number\"",
            "label5:",
            "return"));
  }

  public MethodSignature getMethodSignature(String methodName) {
    return identifierFactory.getMethodSignature(
        methodName, getDeclaredClassSignature(), "void", Collections.emptyList());
  }
}