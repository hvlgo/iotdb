/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.tsfile.fileSystem.fileInputFactory;

import org.apache.iotdb.tsfile.fileSystem.FSPath;
import org.apache.iotdb.tsfile.fileSystem.FSType;
import org.apache.iotdb.tsfile.read.reader.TsFileInput;
import org.apache.iotdb.tsfile.utils.FSUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HybridFileInputFactory implements FileInputFactory {
  private static final Logger logger = LoggerFactory.getLogger(HybridFileInputFactory.class);
  private static final Map<FSType, FileInputFactory> inputFactories = new HashMap<>();

  static {
    inputFactories.put(FSType.LOCAL, new LocalFSInputFactory());
    inputFactories.put(FSType.HDFS, new HDFSInputFactory());
    inputFactories.put(FSType.OBJECT_STORAGE, new OSFileInputFactory());
  }

  @Override
  public TsFileInput getTsFileInput(String filePath) throws IOException {
    FSPath path = FSUtils.parse(filePath);
    return inputFactories.get(path.getFsType()).getTsFileInput(path.getPath());
  }
}
