//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
package edu.iu.dsc.tws.rsched.schedulers.aurora;

import edu.iu.dsc.tws.common.config.Config;
import edu.iu.dsc.tws.common.config.ConfigEntry;
import edu.iu.dsc.tws.rsched.core.SchedulerContext;

/**
 * State parameters for Aurora Client
 */
public class AuroraClientContext extends SchedulerContext {

  public static final ConfigEntry AURORA_SCRIPT = new ConfigEntry(
      "twister2.resource.scheduler.aurora.script", "${TWISTER2_CONF}/twister2.aurora");

  public static final ConfigEntry TWISTER2_PACKAGE_FILE = new ConfigEntry(
      "twister2.resource.scheduler.aurora.package.file", "twister2-client.tar.gz");

  public static final ConfigEntry TWISTER2_PACKAGE_PATH = new ConfigEntry(
      "twister2.resource.scheduler.aurora.package.path", "${TWISTER2_HOME}/");

  public static final String AURORA_CLUSTER_NAME = "twister2.resource.scheduler.aurora.cluster";
  public static final String ROLE = "twister2.resource.scheduler.aurora.role";
  public static final String ENVIRONMENT = "twister2.resource.scheduler.aurora.env";
  public static final String AURORA_JOB_NAME = "twister2.resource.scheduler.aurora.jobname";

  public static final String CPUS_PER_CONTAINER = "twister2.cpu_per_container";
  public static final String RAM_PER_CONTAINER = "twister2.ram_per_container";
  public static final String DISK_PER_CONTAINER = "twister2.disk_per_container";
  public static final String NUMBER_OF_CONTAINERS = "twister2.number_of_containers";

  public static final int DEFAULT_RAM_SIZE = 1073741824; // 1GB
  public static final int DEFAULT_DISK_SIZE = 1073741824; // 1GB

  static {
    defaults.put(AURORA_SCRIPT.getKey(), AURORA_SCRIPT.getDefaultValue());
    defaults.put(TWISTER2_PACKAGE_PATH.getKey(), TWISTER2_PACKAGE_PATH.getDefaultValue());
    defaults.put(TWISTER2_PACKAGE_FILE.getKey(), TWISTER2_PACKAGE_FILE.getDefaultValue());
  }

  public static String auroraScript(Config cfg) {
    return cfg.getStringValue(AURORA_SCRIPT);
  }

  public static String packagePath(Config cfg) {
    return cfg.getStringValue(TWISTER2_PACKAGE_PATH);
  }

  public static String packageFile(Config cfg) {
    return cfg.getStringValue(TWISTER2_PACKAGE_FILE);
  }

  public static String auroraClusterName(Config cfg) {
    return cfg.getStringValue(AURORA_CLUSTER_NAME);
  }

  public static String role(Config cfg) {
    return cfg.getStringValue(ROLE);
  }

  public static String environment(Config cfg) {
    return cfg.getStringValue(ENVIRONMENT);
  }

  public static String auroraJobName(Config cfg) {
    return cfg.getStringValue(AURORA_JOB_NAME);
  }

  public static String cpusPerContainer(Config cfg) {
    return cfg.getStringValue(CPUS_PER_CONTAINER);
  }

  public static int ramPerContainer(Config cfg) {
    return cfg.getIntegerValue(RAM_PER_CONTAINER, DEFAULT_RAM_SIZE);
  }

  public static int diskPerContainer(Config cfg) {
    return cfg.getIntegerValue(DISK_PER_CONTAINER, DEFAULT_DISK_SIZE);
  }

  public static String numberOfContainers(Config cfg) {
    return cfg.getStringValue(NUMBER_OF_CONTAINERS);
  }

}
