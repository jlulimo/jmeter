/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jmeter.protocol.jdbc.sampler;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.jmeter.config.ConfigTestElement;
import org.apache.jmeter.engine.util.ConfigMergabilityIndicator;
import org.apache.jmeter.gui.TestElementMetadata;
import org.apache.jmeter.protocol.jdbc.AbstractJDBCTestElement;
import org.apache.jmeter.protocol.jdbc.config.DataSourceElement;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jorphan.util.JOrphanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A sampler which understands JDBC database requests.
 */
@TestElementMetadata(labelResource = "displayName")
public class JDBCSampler extends AbstractJDBCTestElement implements Sampler, TestBean, ConfigMergabilityIndicator {
    private static Logger logger = LoggerFactory.getLogger(JDBCSampler.class);
    private static AtomicInteger count = new AtomicInteger(0);
    private static final Set<String> APPLIABLE_CONFIG_CLASSES = new HashSet<>(
            Arrays.asList("org.apache.jmeter.config.gui.SimpleConfigGui"));

    private static final long serialVersionUID = 234L;

    /**
     * Creates a JDBCSampler.
     */
    public JDBCSampler() {
    }

    @Override
    public SampleResult sample(Entry e) {
        SampleResult res = new SampleResult();
        res.setSampleLabel(getName());
        res.setSamplerData(toString());
        res.setDataType(SampleResult.TEXT);
        res.setContentType("text/plain"); // $NON-NLS-1$
        res.setDataEncoding(ENCODING);

        // Assume we will be successful
        res.setSuccessful(true);
        res.setResponseMessageOK();
        res.setResponseCodeOK();


        res.sampleStart();
        Connection conn = null;

        try {
            if (JOrphanUtils.isBlank(getDataSource())) {
                throw new IllegalArgumentException("Name for DataSoure must not be empty in " + getName());
            }

            try {
                conn = DataSourceElement.getConnection(getDataSource());
            } finally {
                res.connectEnd();
            }
            JMeterContext threadContext = JMeterContextService.getContext();
            logger.debug("线程:{} 第{}次 执行:{}", threadContext.getThread().getThreadName(), threadContext.getVariables().getIteration(), this.getQuery());
            logger.debug("执行sampler {} 次", count.incrementAndGet());
            res.setResponseHeaders(DataSourceElement.getConnectionInfo(getDataSource()));
            res.setResponseData(execute(conn, res));
        } catch (SQLException ex) {
            final String errCode = Integer.toString(ex.getErrorCode());
            res.setResponseMessage(ex.toString());
            res.setResponseCode(ex.getSQLState() + " " + errCode);
            res.setResponseData(ex.getMessage(), res.getDataEncodingWithDefault());
            res.setSuccessful(false);
        } catch (Exception ex) {
            res.setResponseMessage(ex.toString());
            res.setResponseCode("000");
            res.setResponseData(
                    ObjectUtils.defaultIfNull(ex.getMessage(), "NO MESSAGE"),
                    res.getDataEncodingWithDefault());
            res.setSuccessful(false);
        } finally {
            close(conn);
        }

        // TODO: process warnings? Set Code and Message to success?
        res.sampleEnd();
        return res;
    }

    /**
     * @see org.apache.jmeter.samplers.AbstractSampler#applies(org.apache.jmeter.config.ConfigTestElement)
     */
    @Override
    public boolean applies(ConfigTestElement configElement) {
        String guiClass = configElement.getProperty(TestElement.GUI_CLASS).getStringValue();
        return APPLIABLE_CONFIG_CLASSES.contains(guiClass);
    }
}
