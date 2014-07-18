/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spectator.nflx;

import com.netflix.config.ConfigurationManager;
import com.netflix.governator.annotations.AutoBindSingleton;
import com.netflix.spectator.gc.GcLogger;
import org.apache.commons.configuration.AbstractConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.io.IOException;

/**
 * Plugin for setting up spectator to report correctly into the standard Netflix stack.
 */
@AutoBindSingleton
public final class Plugin {

  private static final String CONFIG_FILE = "spectator.properties";

  private static final GcLogger GC_LOGGER = new GcLogger();

  private static final Logger LOGGER = LoggerFactory.getLogger(Plugin.class);

  @PostConstruct
  private void init() throws IOException {
    ConfigurationManager.loadPropertiesFromResources(CONFIG_FILE);
    AbstractConfiguration config = ConfigurationManager.getConfigInstance();
    if (config.getBoolean("spectator.gc.loggingEnabled")) {
      GC_LOGGER.start(new ChronosGcEventListener());
      LOGGER.info("gc logging started");
    } else {
      LOGGER.info("gc logging is not enabled");
    }
  }
}
