/*
 * Licensed to the Technische Universität Darmstadt under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The Technische Universität Darmstadt
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dkpro.jwpl.revisionmachine.index;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;
import java.util.Properties;

import org.dkpro.jwpl.api.exception.WikiApiException;
import org.dkpro.jwpl.revisionmachine.api.Revision;
import org.dkpro.jwpl.revisionmachine.api.RevisionAPIConfiguration;
import org.dkpro.jwpl.revisionmachine.common.util.Time;
import org.dkpro.jwpl.revisionmachine.difftool.config.OutputTypes;

/**
 * Generates the indices for the database.
 */
public class IndexGenerator {

  /**
   * Reference to the configuration
   */
  private final RevisionAPIConfiguration config;

  /**
   * (Constructor) Creates a new IndexGenerator object.
   *
   * @param config Reference to the configuration
   */
  public IndexGenerator(final RevisionAPIConfiguration config) {
    this.config = config;
  }

  /**
   * Starts the generation of the indices.
   *
   * @throws WikiApiException if an error occurs
   */
  public void generate()
          throws WikiApiException {
    Indexer data = null;
    try {
      data = new Indexer(config);

      System.out.println("GENERATING INDEX STARTED");

      long bufferSize = config.getBufferSize();
      Revision rev;
      long count = 0;
      long last = 0, now, start = System.currentTimeMillis();

      Iterator<Revision> it = new IndexIterator(config);
      while (it.hasNext()) {

        if (++count % bufferSize == 0) {
          now = System.currentTimeMillis() - start;
          System.out.println(Time.toClock(now) + "\t" + (now - last)
                  + "\tINDEXING " + count);
          last = now;
        }

        rev = it.next();
        data.index(rev);
      }

      System.out.println("GENERATING INDEX ENDED + ("
              + Time.toClock(System.currentTimeMillis() - start) + ")");

    } catch (Exception e) {

      throw new WikiApiException(e);

    } finally {
      if (data != null) {
        data.close();
      }
    }
  }

  /**
   * Starts index generation using the database credentials in the
   * properties file specified in args[0].<br>
   * The properties file should have the following structure:
   * <ul><li>host=dbhost</li>
   * <li>db=revisiondb</li>
   * <li>user=username</li>
   * <li>password=pwd</li>
   * <li>output=outputFile</li>
   * <li>writeDirectlyToDB=true|false (optional)</li>
   * <li>charset=UTF8 (or others) (optional)</li>
   * <li>buffer=15000 (optional)</li>
   * <li>maxAllowedPackets=16760832  (optional)</li></ul>
   * <br>
   *
   * @param args allows only one entry that contains the path to the config file
   */
  public static void main(String[] args) {

    if (args == null || args.length != 1) {
      System.out.println(("You need to specify the database configuration file. \n" +
              "It should contain the access credentials to you revision database in the following format: \n" +
              "  host=dbhost \n" +
              "  db=revisiondb \n" +
              "  user=username \n" +
              "  password=pwd \n" +
              "  output=outputFile \n" +
              "  outputDatabase=true|false (optional)\n" +
              "  outputDatafile=true|false (optional)\n" +
              "  charset=UTF8 (optional)\n" +
              "  buffer=15000 (optional)\n" +
              "  maxAllowedPackets=16760832 (optional)\n\n" +
              "  The default output mode is SQL Dump"));
      throw new IllegalArgumentException();
    } else {
      Properties props = load(args[0]);

      RevisionAPIConfiguration config = new RevisionAPIConfiguration();

      config.setHost(props.getProperty("host"));
      config.setDatabase(props.getProperty("db"));
      config.setUser(props.getProperty("user"));
      config.setPassword(props.getProperty("password"));

      String charset = props.getProperty("charset");
      String buffer = props.getProperty("buffer");
      String maxAllowedPackets = props.getProperty("maxAllowedPackets");

      config.setCharacterSet(Objects.requireNonNullElse(charset, "UTF-8"));

      if (buffer != null) {
        config.setBufferSize(Integer.parseInt(buffer));
      } else {
        config.setBufferSize(15000);
      }

      if (maxAllowedPackets != null) {
        config.setMaxAllowedPacket(Long.parseLong(maxAllowedPackets));
      } else {
        config.setMaxAllowedPacket(16 * 1024 * 1023);
      }

      if (props.getProperty("outputDatabase") != null && Boolean.parseBoolean(props.getProperty("outputDatabase"))) {
        config.setOutputType(OutputTypes.DATABASE);
      } else if (props.getProperty("outputDatafile") != null && Boolean.parseBoolean(props.getProperty("outputDatafile"))) {
        config.setOutputType(OutputTypes.DATAFILE);
      } else {
        config.setOutputType(OutputTypes.SQL);
      }

      String output = props.getProperty("output");
      File outfile = new File(output);
      if (outfile.isDirectory()) {
        config.setOutputPath(output);
      } else {
        config.setOutputPath(outfile.getParentFile().getPath());
      }

      try {
        new IndexGenerator(config).generate();
      } catch (Exception e) {
        e.printStackTrace();
      }

      System.out.println("TERMINATED");
    }
  }

  /**
   * Load a properties file from the classpath
   *
   * @param configFilePath path to the configuration file
   * @return Properties the properties object containing the configuration
   * data
   */
  private static Properties load(String configFilePath) {
    Properties props = new Properties();
    BufferedInputStream fis = null;
    try {
      File configFile = new File(configFilePath);
      fis = new BufferedInputStream(new FileInputStream(configFile));
      props.load(fis);
    } catch (IOException e) {
      System.err.println("Could not load configuration file " + configFilePath);
    } finally {
      if (fis != null) {
        try {
          fis.close();
        } catch (IOException e) {
          System.err.println("Error closing file stream of configuration file " + configFilePath);
        }
      }
    }
    return props;
  }

}
