package com.adahas.tools.jmxeval.model.impl;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Properties;

import javax.management.JMException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.TabularDataSupport;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Node;

import com.adahas.tools.jmxeval.Context;
import com.adahas.tools.jmxeval.exception.JMXEvalException;
import com.adahas.tools.jmxeval.model.Element;
import com.adahas.tools.jmxeval.model.PerfDataSupport;

/**
 * Element to configure JMX calls
 */
public class Query extends Element implements PerfDataSupport {

  /**
   * Variable name
   */
  private final Field var;

  /**
   * MBean object name
   */
  private final Field objectName;

  /**
   * MBean attribute
   */
  private final Field attribute;

  /**
   * Composite MBean attribute name (optional)
   */
  private final Field compositeAttribute;

  /**
   * MBean value to use if JMX failure.
   */
  private final Field valueOnFailure;

  /**
   * Constructs the element
   *
   * @param context Execution context
   * @param node XML node
   */
  public Query(final Context context, final Node node) {
    super(context);

    this.var = getNodeAttr(node, "var");
    this.objectName = getNodeAttr(node, "objectName");
    this.attribute = getNodeAttr(node, "attribute");
    this.compositeAttribute = getNodeAttr(node, "compositeAttribute");
    this.valueOnFailure = getNodeAttr(node, "valueOnFailure");
  }

  /**
   * @see Element#process()
   */
  @Override
  public void process() throws JMXEvalException {
    if (context.getConnection() == null) {
      throw new JMXEvalException("Could not connect to server");
    }

    try {
      final ObjectName mbeanName = new ObjectName(objectName.get());
      Object attributeValue;

      // retrieve attribute value
      if (StringUtils.isBlank(compositeAttribute.get())) {
        final Object attributeVal = context.getConnection().getAttribute(mbeanName, attribute.get());
        if (attributeVal instanceof String[]) {
          attributeValue = Arrays.asList((String[]) attributeVal);
        } else {
          attributeValue = attributeVal;
        }
      } else {
        final CompositeData compositeData = (CompositeData) context.getConnection().getAttribute(mbeanName, compositeAttribute.get());
        if (compositeData == null) {
          throw new JMXEvalException("Unable to get composite attribute");
        }
        attributeValue = compositeData.get(attribute.get());
      }

      // Step 1: Check if var.get() contains "PerMinute"
      if (var.get().contains("PerMinute")) {
        String stateFilePath = "C:\\ProgramData\\checkmk\\agent\\mrpe\\jmxeval\\tmp\\statefile.txt"; // set your state file path
        Properties stateProps = new Properties();
        File stateFile = new File(stateFilePath);
        Long previousValue = 0L;
        Long previousTimestamp = 0L;
        BigDecimal rateOfChange;

        // Step 2: Retrieve the previous value of attributeValue from the state file
        if (stateFile.exists()) {
          try (FileInputStream in = new FileInputStream(stateFile)) {
            stateProps.load(in);
            String previousValueString = stateProps.getProperty(var.get());
            String previousTimestampString = stateProps.getProperty(var.get() + "_timestamp");
            if (previousValueString != null) {
              previousValue = Long.parseLong(previousValueString);
            }
            if (previousTimestampString != null) {
              previousTimestamp = Long.parseLong(previousTimestampString);
            }
          } catch (IOException | NumberFormatException e) {
            // Handle exception
            e.printStackTrace();
          }
        } else {
          // If stateFile doesn't exist, set the previous value to the current value
          previousValue = Long.parseLong(attributeValue.toString());
          previousTimestamp = System.currentTimeMillis();
        }

        // Step 3: Calculate the rate of change of attributeValue
        Long currentValue = Long.parseLong(attributeValue.toString());
        Long currentTimestamp = System.currentTimeMillis();
        Long elapsedTimeInMinutes = (currentTimestamp - previousTimestamp) / (1000 * 60);
        elapsedTimeInMinutes = elapsedTimeInMinutes == 0 ? 1 : elapsedTimeInMinutes; // Avoid division by zero
        rateOfChange = BigDecimal.valueOf(previousValue > currentValue ? 0 : (double) (currentValue - previousValue) / elapsedTimeInMinutes).setScale(2, RoundingMode.HALF_UP);

        // Step 4: Save the new value of attributeValue to the state file
        stateProps.setProperty(var.get(), attributeValue.toString());
        stateProps.setProperty(var.get() + "_timestamp", currentTimestamp.toString());
        try {
          if (!stateFile.exists()) {
            // Create directories if they don't exist
            stateFile.getParentFile().mkdirs();
            stateFile.createNewFile();
          }
          try (FileOutputStream out = new FileOutputStream(stateFile)) {
            stateProps.store(out, null);
          }
        } catch (IOException e) {
          // Handle exception
          e.printStackTrace();
        }

        // Set the rate of change as the new attributeValue
        attributeValue = rateOfChange;
      } else if (var.get().contains("CMSOldGen") && attributeValue instanceof TabularDataSupport) {
        attributeValue = getCmsOldGenUsed((TabularDataSupport) attributeValue);
      }

      // set query result as variable
      context.setVar(var.get(), attributeValue);

      // process child elements
      super.process();

    } catch (IOException | JMException | JMXEvalException e) {
      if (valueOnFailure.get() == null) {
        throw new JMXEvalException("Failed to get [" + attribute + "] from [" + objectName + "]" + (compositeAttribute.get() == null ? "" : " in [" + compositeAttribute + "]"), e);
      }

      context.setVar(var.get(), valueOnFailure.get());
    }
  }

  public static Long getCmsOldGenUsed(TabularDataSupport tabularData) throws OpenDataException {
    CompositeDataSupport cmsOldGenData = (CompositeDataSupport) tabularData.get(new Object[] {"CMS Old Gen"});
    CompositeDataSupport memoryUsageData = (CompositeDataSupport) cmsOldGenData.get("value");
    Long usedMemory = (Long) memoryUsageData.get("used");

    return usedMemory;
  }

  /**
   * @see com.adahas.tools.jmxeval.model.PerfDataSupport#getVar()
   */
  @Override
  public Field getVar() {
    return var;
  }

  /**
   * @see com.adahas.tools.jmxeval.model.PerfDataSupport#getCritical()
   */
  @Override
  public Field getCritical() {
    return literalNull();
  }

  /**
   * @see com.adahas.tools.jmxeval.model.PerfDataSupport#getWarning()
   */
  @Override
  public Field getWarning() {
    return literalNull();
  }
}
