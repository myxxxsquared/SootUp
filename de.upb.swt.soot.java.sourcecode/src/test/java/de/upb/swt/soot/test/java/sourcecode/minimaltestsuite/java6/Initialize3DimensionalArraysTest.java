package de.upb.swt.soot.test.java.sourcecode.minimaltestsuite.java6;

import categories.Java8Test;
import de.upb.swt.soot.core.model.SootMethod;
import de.upb.swt.soot.core.signatures.MethodSignature;
import de.upb.swt.soot.test.java.sourcecode.minimaltestsuite.MinimalSourceTestSuiteBase;
import java.util.*;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/** @author Kaustubh Kelkar */
@Category(Java8Test.class)
public class Initialize3DimensionalArraysTest extends MinimalSourceTestSuiteBase {
  @Test
  public void defaultTest() {

    SootMethod method = loadMethod(getMethodSignature("intArrays"));
    assertJimpleStmts(
        method,
        expectedBodyStmts(
            "r0 := @this: Initialize3DimensionalArrays",
            "$r1 = newarray (int[][][])[2]",
            "$r2 = newarray (int[][])[2]",
            "$r3 = newarray (int[])[3]",
            "$r3[0] = 1",
            "$r3[1] = 2",
            "$r3[2] = 3",
            "$r2[0] = $r3",
            "$r4 = newarray (int[])[2]",
            "$r4[0] = 5",
            "$r4[1] = 6",
            "$r2[1] = $r4",
            "$r1[0] = $r2",
            "$r5 = newarray (int[][])[2]",
            "$r6 = newarray (int[])[3]",
            "$r6[0] = 7",
            "$r6[1] = 8",
            "$r6[2] = 9",
            "$r5[0] = $r6",
            "$r7 = newarray (int[])[2]",
            "$r7[0] = 10",
            "$r7[1] = 11",
            "$r5[1] = $r7",
            "$r1[1] = $r5",
            "return"));

    method = loadMethod(getMethodSignature("byteArrays"));

    assertJimpleStmts(
        method,
        expectedBodyStmts(
            "r0 := @this: Initialize3DimensionalArrays",
            "$r1 = newarray (byte[][][])[2]",
            "$r2 = newarray (byte[][])[2]",
            "$r3 = newarray (byte[])[3]",
            "$r3[0] = 7",
            "$r3[1] = 8",
            "$r3[2] = 9",
            "$r2[0] = $r3",
            "$r4 = newarray (byte[])[2]",
            "$r4[0] = 10",
            "$r4[1] = 11",
            "$r2[1] = $r4",
            "$r1[0] = $r2",
            "$r5 = newarray (byte[][])[2]",
            "$r6 = newarray (byte[])[3]",
            "$r6[0] = 1",
            "$r6[1] = 2",
            "$r6[2] = 3",
            "$r5[0] = $r6",
            "$r7 = newarray (byte[])[2]",
            "$r7[0] = 5",
            "$r7[0] = 6",
            "$r5[1] = $r7",
            "$r1[1] = $r5",
            "return"));

    method = loadMethod(getMethodSignature("shortArrays"));
    assertJimpleStmts(
        method,
        expectedBodyStmts(
            "r0 := @this: Initialize3DimensionalArrays",
            "$r1 = newarray (short[][][])[2]",
            "$r2 = newarray (short[][])[2]",
            "$r3 = newarray (short[])[2]",
            "$r3[0] = 10",
            "$r3[1] = 20",
            "$r2[0] = $r2",
            "$r4 = newarray (short[][])[2]",
            "$r5 = newarray (short[])[2]",
            "$r4[0] = 40",
            "$r4[1] = 85",
            "$r2[1] = $r4",
            "$r1[0] = $r2",
            "$r6 = newarray (short[][])[2]",
            "$r7 = newarray (short[])[2]",
            "$r7[0] = 50",
            "$r7[1] = 59",
            "$r6[0] = $r7",
            "$r8 = newarray (short[])[2]",
            "$r8[0] = 95",
            "$r8[1] = 35",
            "$r6[1] = $r8",
            "$r1[1] = $r6",
            "return"));

    method = loadMethod(getMethodSignature("longArrays"));
    assertJimpleStmts(
        method,
        expectedBodyStmts(
            "r0 := @this: Initialize3DimensionalArrays",
            "$r1 = newarray (long[][][])[2]",
            "$r2 = newarray (long[][])[2]",
            "$r3 = newarray (long[])[2]",
            "$r3[0] = 547087L",
            "$r3[1] = 654786L",
            "$r2[0] = $r2",
            "$r4 = newarray (long[][])[2]",
            "$r5 = newarray (long[])[3]",
            "$r4[0] = 547287L",
            "$r4[1] = 864645L",
            "$r4[2] = 6533786L",
            "$r2[1] = $r4",
            "$r1[0] = $r2",
            "$r6 = newarray (long[][])[2]",
            "$r7 = newarray (long[])[2]",
            "$r7[0] = 34565L",
            "$r7[1] = 234L",
            "$r6[0] = $r7",
            "$r8 = newarray (long[])[2]",
            "$r8[0] = 9851L",
            "$r8[1] = 63543L",
            "$r6[1] = $r8",
            "$r1[1] = $r6",
            "return"));

    method = loadMethod(getMethodSignature("floatArrays"));

    assertJimpleStmts(
        method,
        expectedBodyStmts(
            "r0 := @this: Initialize3DimensionalArrays",
            "$r1 = newarray (float[][][])[2]",
            "$r2 = newarray (float[][])[2]",
            "$r3 = newarray (float[])[2]",
            "$r3[0] = 3.14F",
            "$r3[1] = 5.46F",
            "$r2[0] = $r2",
            "$r4 = newarray (float[][])[2]",
            "$r5 = newarray (float[])[2]",
            "$r4[0] = 2.987F",
            "$r4[1] = 4.87F",
            "$r2[1] = $r4",
            "$r1[0] = $r2",
            "$r6 = newarray (float[][])[2]",
            "$r7 = newarray (float[])[2]",
            "$r7[0] = 65.15F",
            "$r7[1] = 854.18F",
            "$r6[0] = $r7",
            "$r8 = newarray (float[])[2]",
            "$r8[0] = 16.51F",
            "$r8[1] = 58.14F",
            "$r6[1] = $r8",
            "$r1[1] = $r6",
            "return"));

    method = loadMethod(getMethodSignature("doubleArrays"));
    assertJimpleStmts(
        method,
        expectedBodyStmts(
            "r0 := @this: Initialize3DimensionalArrays",
            "$r1 = newarray (double[][][])[2]",
            "$r2 = newarray (double[][])[2]",
            "$r3 = newarray (double[])[2]",
            "$r3[0] = 6.765414",
            "$r3[1] = 9.676565646",
            "$r2[0] = $r2",
            "$r4 = newarray (double[][])[2]",
            "$r5 = newarray (double[])[1]",
            "$r4[0] = 45.345435",
            "$r2[1] = $r4",
            "$r1[0] = $r2",
            "$r6 = newarray (double[][])[2]",
            "$r7 = newarray (double[])[2]",
            "$r7[0] = 3.5656",
            "$r7[1] = 68.234234",
            "$r6[0] = $r7",
            "$r8 = newarray (double[])[2]",
            "$r8[0] = 68416.651",
            "$r8[1] = 65416.5",
            "$r6[1] = $r8",
            "$r1[1] = $r6",
            "return"));

    method = loadMethod(getMethodSignature("booleanArrays"));
    assertJimpleStmts(
        method,
        expectedBodyStmts(
            "r0 := @this: Initialize3DimensionalArrays",
            "$r1 = newarray (boolean[][][])[2]",
            "$r2 = newarray (boolean[][])[2]",
            "$r3 = newarray (boolean[])[2]",
            "$r3[0] = 1",
            "$r3[1] = 0",
            "$r2[0] = $r2",
            "$r4 = newarray (boolean[][])[2]",
            "$r5 = newarray (boolean[])[1]",
            "$r4[0] = 1",
            "$r2[1] = $r4",
            "$r1[0] = $r2",
            "$r6 = newarray (boolean[][])[2]",
            "$r7 = newarray (boolean[])[2]",
            "$r7[0] = 0",
            "$r7[1] = 0",
            "$r6[0] = $r7",
            "$r8 = newarray (boolean[])[1]",
            "$r8[0] = 1",
            "$r6[1] = $r8",
            "$r1[1] = $r6",
            "return"));

    method = loadMethod(getMethodSignature("charArrays"));
    assertJimpleStmts(
        method,
        expectedBodyStmts(
            "r0 := @this: Initialize3DimensionalArrays",
            "$r1 = newarray (boolean[][][])[2]",
            "$r2 = newarray (boolean[][])[2]",
            "$r3 = newarray (boolean[])[3]",
            "$r3[0] = 65",
            "$r3[1] = 98",
            "$r3[1] = 38",
            "$r2[0] = $r2",
            "$r4 = newarray (boolean[][])[2]",
            "$r5 = newarray (boolean[])[2]",
            "$r4[0] = 99",
            "$r4[1] = 36",
            "$r2[1] = $r4",
            "$r1[0] = $r2",
            "$r6 = newarray (boolean[][])[2]",
            "$r7 = newarray (boolean[])[2]",
            "$r7[0] = 50",
            "$r7[1] = 71",
            "$r6[0] = $r7",
            "$r8 = newarray (boolean[])[1]",
            "$r8[0] = 97",
            "$r8[1] = 37",
            "$r6[1] = $r8",
            "$r1[1] = $r6",
            "return"));

    method = loadMethod(getMethodSignature("stringArrays"));
    assertJimpleStmts(
        method,
        expectedBodyStmts(
            "r0 := @this: Initialize3DimensionalArrays",
            "$r1 = newarray (java.lang.String[][][])[2]",
            "$r2 = newarray (java.lang.String[][])[2]",
            "$r3 = newarray (java.lang.String[])[1]",
            "$r3[0] = \"Hello World\"",
            "$r2[0] = $r3",
            "$r4 = newarray (java.lang.String[][])[2]",
            "$r5 = newarray (java.lang.String[])[2]",
            "$r5[0] = \"Greetings\"",
            "$r5[1] = \"Welcome\"",
            "$r2[1] = $r5",
            "$r1[0] = $r2",
            "$r6 = newarray (java.lang.String[][])[2]",
            "$r7 = newarray (java.lang.String[])[2]",
            "$r7[0] = \"Future\"",
            "$r7[1] = \"Soot\"",
            "$r6[0] = $r7",
            "$r8 = newarray (java.lang.String[])[1]",
            "$r8[0] = \"UPB\"",
            "$r8[1] = \"HNI\"",
            "$r6[1] = $r8",
            "$r1[1] = $r6",
            "return"));
  }

  public MethodSignature getMethodSignature(String methodName) {
    return identifierFactory.getMethodSignature(
        methodName, getDeclaredClassSignature(), "void", Collections.emptyList());
  }
}
