/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.drill;

import org.apache.drill.common.exceptions.UserException;
import org.apache.drill.exec.memory.OutOfMemoryRuntimeException;
import org.apache.drill.exec.memory.TopLevelAllocator;
import org.apache.drill.exec.proto.CoordinationProtos;
import org.apache.drill.exec.proto.UserBitShared.DrillPBError;
import org.apache.drill.exec.testing.ControlsInjectionUtil;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

/**
 * Run several tpch queries and inject an OutOfMemoryException in ScanBatch that will cause an OUT_OF_MEMORY outcome to
 * be propagated downstream. Make sure the proper "memory error" message is sent to the client.
 */
public class TestAllocationException extends BaseTestQuery {

  private static final String SINGLE_MODE = "ALTER SESSION SET `planner.disable_exchanges` = true";

  private void testWithException(final String fileName) throws Exception {
    testWithException(fileName, OutOfMemoryRuntimeException.class);
  }

  private void testWithException(final String fileName, Class<? extends Throwable> exceptionClass) throws Exception{
    test(SINGLE_MODE);

    CoordinationProtos.DrillbitEndpoint endpoint = bits[0].getContext().getEndpoint();

    String controlsString = "{\"injections\":[{"
      + "\"address\":\"" + endpoint.getAddress() + "\","
      + "\"port\":\"" + endpoint.getUserPort() + "\","
      + "\"type\":\"exception\","
      + "\"siteClass\":\"" + TopLevelAllocator.class.getName() + "\","
      + "\"desc\":\"" + TopLevelAllocator.CHILD_BUFFER_INJECTION_SITE + "\","
      + "\"nSkip\":200,"
      + "\"nFire\":1,"
      + "\"exceptionClass\":\"" + exceptionClass.getName() + "\""
      + "}]}";
    ControlsInjectionUtil.setControls(client, controlsString);

    String query = getFile(fileName);

    try {
      test(query);
      fail("The query should have failed!");
    } catch(UserException uex) {
      DrillPBError error = uex.getOrCreatePBError(false);
      assertEquals(DrillPBError.ErrorType.RESOURCE, error.getErrorType());
      assertTrue("Error message isn't related to memory error",
        uex.getMessage().contains(UserException.MEMORY_ERROR_MSG));
    }
  }

  @Test
  public void testWithNull() throws Exception{
    testWithException("queries/tpch/01.sql");
  }

  @Test
  public void testWithOOM() throws Exception{
    testWithException("queries/tpch/03.sql", NullPointerException.class);
  }
}
