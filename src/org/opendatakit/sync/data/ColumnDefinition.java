/*
 * Copyright (C) 2014 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opendatakit.sync.data;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.opendatakit.aggregate.odktables.rest.ElementDataType;
import org.opendatakit.aggregate.odktables.rest.ElementType;
import org.opendatakit.aggregate.odktables.rest.TableConstants;
import org.opendatakit.aggregate.odktables.rest.entity.Column;
import org.opendatakit.sync.data.NameUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// TODO: This class will be available via the REST jar eventually and 
// is being used here until that is complete.
public class ColumnDefinition implements Comparable<ColumnDefinition> {
  private static final String TAG = "ColumnDefinition";
  private static final String JSON_SCHEMA_NOT_UNIT_OF_RETENTION = "notUnitOfRetention";
  private static final String JSON_SCHEMA_IS_NOT_NULLABLE = "isNotNullable";
  private static final String JSON_SCHEMA_ELEMENT_SET = "elementSet";
  private static final String JSON_SCHEMA_INSTANCE_METADATA_VALUE = "instanceMetadata";
  private static final String JSON_SCHEMA_INSTANCE_DATA_VALUE = "data";
  private static final String JSON_SCHEMA_DEFAULT = "default";
  // the database column under which this data is persisted (e.g., myPoint_latitude)
  private static final String JSON_SCHEMA_ELEMENT_KEY = "elementKey";
  // the Javascript path to this data if reconstructed into JS object (e.g., myPoint.latitude)
  private static final String JSON_SCHEMA_ELEMENT_PATH = "elementPath";
  // the Javascript name for this element within a Javascript path (e.g., latitude) (rightmost term)
  private static final String JSON_SCHEMA_ELEMENT_NAME = "elementName";
  private static final String JSON_SCHEMA_ELEMENT_TYPE = "elementType";
  private static final String JSON_SCHEMA_LIST_CHILD_ELEMENT_KEYS = "listChildElementKeys";
  private static final String JSON_SCHEMA_PROPERTIES = "properties";
  private static final String JSON_SCHEMA_ITEMS = "items";
  private static final String JSON_SCHEMA_TYPE = "type";

  private final Column column;

  // public final String elementKey;
  // public final String elementName;
  // public final String elementType;
  private boolean isUnitOfRetention = true; // assumed until revised...

  final ArrayList<ColumnDefinition> children = new ArrayList<ColumnDefinition>();
  ElementType type = null;
  ColumnDefinition parent = null;

  ColumnDefinition(String elementKey, String elementName, String elementType,
      String listChildElementKeys) {
    this.column = new Column(elementKey, elementName, elementType, listChildElementKeys);
  }

  public String getElementKey() {
    return column.getElementKey();
  }

  public String getElementName() {
    return column.getElementName();
  }

  public String getElementType() {
    return column.getElementType();
  }
  
  public synchronized ElementType getType() {
    if ( type == null ) {
      type = ElementType.parseElementType(getElementType(), !getChildren().isEmpty());
    }
    return type;
  }

  public String getListChildElementKeys() {
    return column.getListChildElementKeys();
  }

  private void setParent(ColumnDefinition parent) {
    this.parent = parent;
  }

  public ColumnDefinition getParent() {
    return this.parent;
  }

  public void addChild(ColumnDefinition child) {
    child.setParent(this);
    children.add(child);
  }

  public List<ColumnDefinition> getChildren() {
    return Collections.unmodifiableList(this.children);
  }

  public boolean isUnitOfRetention() {
    return isUnitOfRetention;
  }

  void setNotUnitOfRetention() {
    isUnitOfRetention = false;
  }

  public String toString() {
    return column.toString();
  }

  public int hashCode() {
    return column.hashCode();
  }

  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof ColumnDefinition)) {
      return false;
    }
    ColumnDefinition o = (ColumnDefinition) obj;

    return column.equals(o.column);
  }

  /**
   * Binary search using elementKey. The ColumnDefinition list returned by
   * ColumnDefinition.buildColumnDefinitions() is ordered. This function makes
   * use of that property to quickly retrieve the definition for an elementKey.
   * 
   * @param orderedDefns
   * @param elementKey
   * @return ColumnDefinition
   * @throws IllegalArgumentException - if elementKey not found
   */
  static ColumnDefinition find(ArrayList<ColumnDefinition> orderedDefns, String elementKey)
      throws IllegalArgumentException {
    if (elementKey == null) {
      throw new NullPointerException("elementKey cannot be null in ColumnDefinition::find()");
    }
    int iLow = 0;
    int iHigh = orderedDefns.size();
    int iGuess = (iLow + iHigh) / 2;
    while (iLow != iHigh) {
      ColumnDefinition cd = orderedDefns.get(iGuess);
      int cmp = elementKey.compareTo(cd.getElementKey());
      if (cmp == 0) {
        return cd;
      }
      if (cmp < 0) {
        iHigh = iGuess;
      } else {
        iLow = iGuess + 1;
      }
      iGuess = (iLow + iHigh) / 2;
    }

    if (iLow >= orderedDefns.size()) {
      throw new IllegalArgumentException("could not find elementKey in columns list: " + elementKey);
    }

    ColumnDefinition cd = orderedDefns.get(iGuess);
    if (cd.getElementKey().equals(elementKey)) {
      return cd;
    }
    throw new IllegalArgumentException("could not find elementKey in columns list: " + elementKey);
  }

  /**
   * Helper class for building ColumnDefinition objects from Column objects.
   * 
   * @author mitchellsundt@gmail.com
   */
  private static class ColumnContainer {
    public ColumnDefinition defn = null;
    public ArrayList<String> children = null;
  }

  /**
   * Construct the rich ColumnDefinition objects for a table from the underlying
   * information in the list of Column objects.
   * 
   * @param appName the Aggregate appName
   * @param tableId the tableId
   * @param columns the columns to use
   * @return defns ArrayList of built column definitions
   */
  @SuppressWarnings("unchecked")
  public static final ArrayList<ColumnDefinition> buildColumnDefinitions(String appName, String tableId, List<Column> columns) {

     if ( appName == null || appName.length() == 0 ) {
        throw new IllegalArgumentException("appName cannot be null or an empty string");
     }

     if ( tableId == null || tableId.length() == 0 ) {
        throw new IllegalArgumentException("tableId cannot be null or an empty string");
     }

    if ( columns == null ) {
       throw new IllegalArgumentException("columns cannot be null");
    }
    
    ObjectMapper mapper = new ObjectMapper();

    System.out.println("[buildColumnDefinitions] tableId: " + tableId + " size: " + columns.size() + " first column: " + 
        (columns.isEmpty() ? "<none>" : columns.get(0).getElementKey()));
    
    Map<String, ColumnDefinition> colDefs = new HashMap<String, ColumnDefinition>();
    List<ColumnContainer> ccList = new ArrayList<ColumnContainer>();
    for (Column col : columns) {
      if (!NameUtil.isValidUserDefinedDatabaseName(col.getElementKey())) {
        throw new IllegalArgumentException("ColumnDefinition: invalid user-defined column name: "
            + col.getElementKey());
      }
      ColumnDefinition cd = new ColumnDefinition(col.getElementKey(), col.getElementName(),
          col.getElementType(), col.getListChildElementKeys());
      ColumnContainer cc = new ColumnContainer();
      cc.defn = cd;
      String children = col.getListChildElementKeys();
      if (children != null && children.length() != 0) {
        ArrayList<String> chi;
        try {
          chi = mapper.readValue(children, ArrayList.class);
        } catch (JsonParseException e) {
          e.printStackTrace();
          throw new IllegalArgumentException("Invalid list of children: " + children);
        } catch (JsonMappingException e) {
          e.printStackTrace();
          throw new IllegalArgumentException("Invalid list of children: " + children);
        } catch (IOException e) {
          e.printStackTrace();
          throw new IllegalArgumentException("Invalid list of children: " + children);
        }
        cc.children = chi;
        ccList.add(cc);
      }
      colDefs.put(cd.getElementKey(), cd);
    }
    for (ColumnContainer cc : ccList) {
      ColumnDefinition cparent = cc.defn;
      for (String childKey : cc.children) {
        ColumnDefinition cchild = colDefs.get(childKey);
        if (cchild == null) {
          throw new IllegalArgumentException("Child elementkey " + childKey
              + " was never defined but referenced in " + cparent.getElementKey() + "!");
        }
        // set up bi-directional linkage of child and parent.
        cparent.addChild(cchild);
      }
    }

    // Sanity check:
    // (1) all children elementKeys must have been defined in the Columns list.
    // (2) arrays must have only one child.
    // (3) children must belong to at most one parent
    for (ColumnContainer cc : ccList) {
      ColumnDefinition defn = cc.defn;

      if (defn.getChildren().size() != cc.children.size()) {
        throw new IllegalArgumentException("Not all children of element have been defined! "
            + defn.getElementKey());
      }

      ElementType type = defn.getType();

      if (type.getDataType() == ElementDataType.array) {
        if (defn.getChildren().isEmpty()) {
          throw new IllegalArgumentException("Column is an array but does not list its children");
        }
        if (defn.getChildren().size() != 1) {
          throw new IllegalArgumentException("Column is an array but has more than one item entry");
        }
      }

      for (ColumnDefinition child : defn.getChildren()) {
        if (child.getParent() != defn) {
          throw new IllegalArgumentException("Column is enclosed by two or more groupings: "
              + defn.getElementKey());
        }
        if (!child.getElementKey().equals(defn.getElementKey() + "_" + child.getElementName())) {
          throw new IllegalArgumentException(
              "Children are expected to have elementKey equal to parent's "
                  + "elementKey-underscore-childElementName: " + child.getElementKey());
        }
      }
    }
    markUnitOfRetention(colDefs);
    ArrayList<ColumnDefinition> defns = new ArrayList<ColumnDefinition>(colDefs.values());
    Collections.sort(defns);

    return defns;
  }

  /**
   * This must match the code in the javascript layer
   * 
   * See databaseUtils.markUnitOfRetention
   * 
   * Sweeps through the collection of ColumnDefinition objects and marks the
   * ones that exist in the actual database table.
   * 
   * @param defn
   */
  private static void markUnitOfRetention(Map<String, ColumnDefinition> defn) {
    // for all arrays, mark all descendants of the array as not-retained
    // because they are all folded up into the json representation of the array
    for (String startKey : defn.keySet()) {
      ColumnDefinition colDefn = defn.get(startKey);
      if (!colDefn.isUnitOfRetention()) {
        // this has already been processed
        continue;
      }
      ElementType type = colDefn.getType();
      if (ElementDataType.array == type.getDataType()) {
        ArrayList<ColumnDefinition> descendantsOfArray = new ArrayList<ColumnDefinition>(
            colDefn.getChildren());
        ArrayList<ColumnDefinition> scratchArray = new ArrayList<ColumnDefinition>();
        for (;;) {
          for (ColumnDefinition subDefn : descendantsOfArray) {
            if (!subDefn.isUnitOfRetention()) {
              // this has already been processed
              continue;
            }
            subDefn.setNotUnitOfRetention();
            scratchArray.addAll(subDefn.getChildren());
          }

          descendantsOfArray.clear();
          descendantsOfArray.addAll(scratchArray);
          scratchArray.clear();
          if ( descendantsOfArray.isEmpty()) {
            break;
          }
        }
      }
    }
    // and mark any non-arrays with multiple fields as not retained
    for (String startKey : defn.keySet()) {
      ColumnDefinition colDefn = defn.get(startKey);
      if (!colDefn.isUnitOfRetention()) {
        // this has already been processed
        continue;
      }
      ElementType type = colDefn.getType();
      if (ElementDataType.array != type.getDataType()) {
        if (!colDefn.getChildren().isEmpty()) {
          colDefn.setNotUnitOfRetention();
        }
      }
    }
  }

  /**
   * Convert the ColumnDefinition map to an ordered list of columns for
   * transport layer.
   * 
   * @param orderedDefns ColumnDefinition map
   * @return ordered list of Column objects
   */
  public static ArrayList<Column> getColumns(ArrayList<ColumnDefinition> orderedDefns) {
    ArrayList<Column> columns = new ArrayList<Column>();
    for (ColumnDefinition col : orderedDefns) {
      columns.add(col.column);
    }
    return columns;
  }

  /**
   * Covert the ColumnDefinition map into a JSON schema. and augment it with
   * the schema for the administrative columns.
   *
   * The structure of this schema matches the dataTableModel produced by XLSXConverter
   *
   * @param orderedDefns
   * @return TreeMap<String, Object>
   */
  // TODO: This may or may not be available via the REST jar - will need to resolve then   
  static TreeMap<String, Object> getExtendedDataModel(List<ColumnDefinition> orderedDefns) {
    TreeMap<String, Object> model = getDataModel(orderedDefns);

    TreeMap<String, Object> jsonSchema;
    //
    jsonSchema = new TreeMap<String, Object>();
    model.put(TableConstants.ID, jsonSchema);
    jsonSchema.put(JSON_SCHEMA_TYPE, ElementDataType.string.name());
    jsonSchema.put(JSON_SCHEMA_ELEMENT_SET, JSON_SCHEMA_INSTANCE_METADATA_VALUE);
    jsonSchema.put(JSON_SCHEMA_IS_NOT_NULLABLE, Boolean.TRUE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_KEY, TableConstants.ID);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_NAME, TableConstants.ID);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_PATH, TableConstants.ID);
    //
    jsonSchema = new TreeMap<String, Object>();
    model.put(TableConstants.ROW_ETAG, jsonSchema);
    jsonSchema.put(JSON_SCHEMA_TYPE, ElementDataType.string.name());
    jsonSchema.put(JSON_SCHEMA_ELEMENT_SET, JSON_SCHEMA_INSTANCE_METADATA_VALUE);
    jsonSchema.put(JSON_SCHEMA_IS_NOT_NULLABLE, Boolean.FALSE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_KEY, TableConstants.ROW_ETAG);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_NAME, TableConstants.ROW_ETAG);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_PATH, TableConstants.ROW_ETAG);
    //
    jsonSchema = new TreeMap<String, Object>();
    model.put(TableConstants.SYNC_STATE, jsonSchema);
    jsonSchema.put(JSON_SCHEMA_TYPE, ElementDataType.string.name());
    jsonSchema.put(JSON_SCHEMA_ELEMENT_SET, JSON_SCHEMA_INSTANCE_METADATA_VALUE);
    jsonSchema.put(JSON_SCHEMA_IS_NOT_NULLABLE, Boolean.TRUE);
    // don't force a default value -- the database layer handles sync state initialization itself.
    jsonSchema.put(JSON_SCHEMA_ELEMENT_KEY, TableConstants.SYNC_STATE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_NAME, TableConstants.SYNC_STATE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_PATH, TableConstants.SYNC_STATE);
    //
    jsonSchema = new TreeMap<String, Object>();
    model.put(TableConstants.CONFLICT_TYPE, jsonSchema);
    jsonSchema.put(JSON_SCHEMA_TYPE, ElementDataType.integer.name());
    jsonSchema.put(JSON_SCHEMA_ELEMENT_SET, JSON_SCHEMA_INSTANCE_METADATA_VALUE);
    jsonSchema.put(JSON_SCHEMA_IS_NOT_NULLABLE, Boolean.FALSE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_KEY, TableConstants.CONFLICT_TYPE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_NAME, TableConstants.CONFLICT_TYPE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_PATH, TableConstants.CONFLICT_TYPE);
    //
    jsonSchema = new TreeMap<String, Object>();
    model.put(TableConstants.DEFAULT_ACCESS, jsonSchema);
    jsonSchema.put(JSON_SCHEMA_TYPE, ElementDataType.string.name());
    jsonSchema.put(JSON_SCHEMA_ELEMENT_SET, JSON_SCHEMA_INSTANCE_METADATA_VALUE);
    jsonSchema.put(JSON_SCHEMA_IS_NOT_NULLABLE, Boolean.FALSE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_KEY, TableConstants.DEFAULT_ACCESS);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_NAME, TableConstants.DEFAULT_ACCESS);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_PATH, TableConstants.DEFAULT_ACCESS);
    //
    jsonSchema = new TreeMap<String, Object>();
    model.put(TableConstants.ROW_OWNER, jsonSchema);
    jsonSchema.put(JSON_SCHEMA_TYPE, ElementDataType.string.name());
    jsonSchema.put(JSON_SCHEMA_IS_NOT_NULLABLE, Boolean.FALSE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_SET, JSON_SCHEMA_INSTANCE_METADATA_VALUE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_KEY, TableConstants.ROW_OWNER);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_NAME, TableConstants.ROW_OWNER);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_PATH, TableConstants.ROW_OWNER);
    //
    jsonSchema = new TreeMap<String, Object>();
    model.put(TableConstants.GROUP_READ_ONLY, jsonSchema);
    jsonSchema.put(JSON_SCHEMA_TYPE, ElementDataType.string.name());
    jsonSchema.put(JSON_SCHEMA_IS_NOT_NULLABLE, Boolean.FALSE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_SET, JSON_SCHEMA_INSTANCE_METADATA_VALUE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_KEY, TableConstants.GROUP_READ_ONLY);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_NAME, TableConstants.GROUP_READ_ONLY);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_PATH, TableConstants.GROUP_READ_ONLY);
    //
    jsonSchema = new TreeMap<String, Object>();
    model.put(TableConstants.GROUP_MODIFY, jsonSchema);
    jsonSchema.put(JSON_SCHEMA_TYPE, ElementDataType.string.name());
    jsonSchema.put(JSON_SCHEMA_IS_NOT_NULLABLE, Boolean.FALSE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_SET, JSON_SCHEMA_INSTANCE_METADATA_VALUE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_KEY, TableConstants.GROUP_MODIFY);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_NAME, TableConstants.GROUP_MODIFY);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_PATH, TableConstants.GROUP_MODIFY);
    //
    jsonSchema = new TreeMap<String, Object>();
    model.put(TableConstants.GROUP_PRIVILEGED, jsonSchema);
    jsonSchema.put(JSON_SCHEMA_TYPE, ElementDataType.string.name());
    jsonSchema.put(JSON_SCHEMA_IS_NOT_NULLABLE, Boolean.FALSE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_SET, JSON_SCHEMA_INSTANCE_METADATA_VALUE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_KEY, TableConstants.GROUP_PRIVILEGED);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_NAME, TableConstants.GROUP_PRIVILEGED);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_PATH, TableConstants.GROUP_PRIVILEGED);
    //
    jsonSchema = new TreeMap<String, Object>();
    model.put(TableConstants.FORM_ID, jsonSchema);
    jsonSchema.put(JSON_SCHEMA_TYPE, ElementDataType.string.name());
    jsonSchema.put(JSON_SCHEMA_IS_NOT_NULLABLE, Boolean.FALSE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_SET, JSON_SCHEMA_INSTANCE_METADATA_VALUE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_KEY, TableConstants.FORM_ID);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_NAME, TableConstants.FORM_ID);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_PATH, TableConstants.FORM_ID);
    //
    jsonSchema = new TreeMap<String, Object>();
    model.put(TableConstants.LOCALE, jsonSchema);
    jsonSchema.put(JSON_SCHEMA_TYPE, ElementDataType.string.name());
    jsonSchema.put(JSON_SCHEMA_IS_NOT_NULLABLE, Boolean.FALSE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_SET, JSON_SCHEMA_INSTANCE_METADATA_VALUE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_KEY, TableConstants.LOCALE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_NAME, TableConstants.LOCALE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_PATH, TableConstants.LOCALE);
    //
    jsonSchema = new TreeMap<String, Object>();
    model.put(TableConstants.SAVEPOINT_TYPE, jsonSchema);
    jsonSchema.put(JSON_SCHEMA_TYPE, ElementDataType.string.name());
    jsonSchema.put(JSON_SCHEMA_IS_NOT_NULLABLE, Boolean.FALSE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_SET, JSON_SCHEMA_INSTANCE_METADATA_VALUE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_KEY, TableConstants.SAVEPOINT_TYPE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_NAME, TableConstants.SAVEPOINT_TYPE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_PATH, TableConstants.SAVEPOINT_TYPE);
    //
    jsonSchema = new TreeMap<String, Object>();
    model.put(TableConstants.SAVEPOINT_TIMESTAMP, jsonSchema);
    jsonSchema.put(JSON_SCHEMA_TYPE, ElementDataType.string.name());
    jsonSchema.put(JSON_SCHEMA_IS_NOT_NULLABLE, Boolean.TRUE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_SET, JSON_SCHEMA_INSTANCE_METADATA_VALUE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_KEY, TableConstants.SAVEPOINT_TIMESTAMP);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_NAME, TableConstants.SAVEPOINT_TIMESTAMP);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_PATH, TableConstants.SAVEPOINT_TIMESTAMP);
    //
    jsonSchema = new TreeMap<String, Object>();
    model.put(TableConstants.SAVEPOINT_CREATOR, jsonSchema);
    jsonSchema.put(JSON_SCHEMA_TYPE, ElementDataType.string.name());
    jsonSchema.put(JSON_SCHEMA_IS_NOT_NULLABLE, Boolean.FALSE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_SET, JSON_SCHEMA_INSTANCE_METADATA_VALUE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_KEY, TableConstants.SAVEPOINT_CREATOR);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_NAME, TableConstants.SAVEPOINT_CREATOR);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_PATH, TableConstants.SAVEPOINT_CREATOR);

    return model;
  }

  /**
   * Covert the ColumnDefinition map into a JSON schema.
   *
   * Add elements to this schema to match the dataTableModel produced by XLSXConverter
   *
   * @param orderedDefns
   * @return TreeMap<String, Object>
   */
  static TreeMap<String, Object> getDataModel(List<ColumnDefinition> orderedDefns) {
    TreeMap<String, Object> model = new TreeMap<String, Object>();

    for (ColumnDefinition c : orderedDefns) {
      if (c.getParent() == null) {
        TreeMap<String, Object> jsonSchema = new TreeMap<String, Object>();
        model.put(c.getElementName(), jsonSchema);
        jsonSchema.put(JSON_SCHEMA_ELEMENT_PATH, c.getElementName());
        getDataModelHelper(jsonSchema, c, false);
        if ( !c.isUnitOfRetention() ) {
          jsonSchema.put(JSON_SCHEMA_NOT_UNIT_OF_RETENTION, Boolean.TRUE);
        }
      }
    }
    return model;
  }

  private static void getDataModelHelper(TreeMap<String, Object> jsonSchema, ColumnDefinition c,
      boolean nestedInsideUnitOfRetention) {
    ElementType type = c.getType();
    ElementDataType dataType = type.getDataType();

    // this is a user-defined field
    jsonSchema.put(JSON_SCHEMA_ELEMENT_SET, JSON_SCHEMA_INSTANCE_DATA_VALUE);
    jsonSchema.put(JSON_SCHEMA_ELEMENT_NAME, c.getElementName());
    jsonSchema.put(JSON_SCHEMA_ELEMENT_KEY, c.getElementKey());

    if ( nestedInsideUnitOfRetention ) {
      jsonSchema.put(JSON_SCHEMA_NOT_UNIT_OF_RETENTION, Boolean.TRUE);
    }

    if (dataType == ElementDataType.array) {
      jsonSchema.put(JSON_SCHEMA_TYPE, dataType.name());
      if (!c.getElementType().equals(dataType.name())) {
        jsonSchema.put(JSON_SCHEMA_ELEMENT_TYPE, c.getElementType());
      }
      ColumnDefinition ch = c.getChildren().get(0);
      TreeMap<String, Object> itemSchema = new TreeMap<String, Object>();
      jsonSchema.put(JSON_SCHEMA_ITEMS, itemSchema);
      itemSchema.put(JSON_SCHEMA_ELEMENT_PATH,
          ((String) jsonSchema.get(JSON_SCHEMA_ELEMENT_PATH)) + '.' + ch.getElementName());
      // if it isn't already nested within a unit of retention,
      // an array is always itself a unit of retention
      getDataModelHelper(itemSchema, ch, true); // recursion...

      ArrayList<String> keys = new ArrayList<String>();
      keys.add(ch.getElementKey());
      jsonSchema.put(JSON_SCHEMA_LIST_CHILD_ELEMENT_KEYS, keys);
    } else if (dataType == ElementDataType.bool) {
      jsonSchema.put(JSON_SCHEMA_TYPE, dataType.name());
      if (!c.getElementType().equals(dataType.name())) {
        jsonSchema.put(JSON_SCHEMA_ELEMENT_TYPE, c.getElementType());
      }
    } else if (dataType == ElementDataType.configpath) {
      jsonSchema.put(JSON_SCHEMA_TYPE, ElementDataType.string.name());
      jsonSchema.put(JSON_SCHEMA_ELEMENT_TYPE, c.getElementType());
    } else if (dataType == ElementDataType.integer) {
      jsonSchema.put(JSON_SCHEMA_TYPE, dataType.name());
      if (!c.getElementType().equals(dataType.name())) {
        jsonSchema.put(JSON_SCHEMA_ELEMENT_TYPE, c.getElementType());
      }
    } else if (dataType == ElementDataType.number) {
      jsonSchema.put(JSON_SCHEMA_TYPE, dataType.name());
      if (!c.getElementType().equals(dataType.name())) {
        jsonSchema.put(JSON_SCHEMA_ELEMENT_TYPE, c.getElementType());
      }
    } else if (dataType == ElementDataType.object) {
      jsonSchema.put(JSON_SCHEMA_TYPE, dataType.name());
      if (!c.getElementType().equals(dataType.name())) {
        jsonSchema.put(JSON_SCHEMA_ELEMENT_TYPE, c.getElementType());
      }
      TreeMap<String, Object> propertiesSchema = new TreeMap<String, Object>();
      jsonSchema.put(JSON_SCHEMA_PROPERTIES, propertiesSchema);
      ArrayList<String> keys = new ArrayList<String>();
      for (ColumnDefinition ch : c.getChildren()) {
        TreeMap<String, Object> itemSchema = new TreeMap<String, Object>();
        propertiesSchema.put(ch.getElementName(), itemSchema);
        itemSchema.put(JSON_SCHEMA_ELEMENT_PATH,
            ((String) jsonSchema.get(JSON_SCHEMA_ELEMENT_PATH)) + '.' + ch.getElementName());
        // objects are not units of retention -- propagate retention status.
        getDataModelHelper(itemSchema, ch, nestedInsideUnitOfRetention); // recursion...
        keys.add(ch.getElementKey());
      }
      jsonSchema.put(JSON_SCHEMA_LIST_CHILD_ELEMENT_KEYS, keys);
    } else if (dataType == ElementDataType.rowpath) {
      jsonSchema.put(JSON_SCHEMA_TYPE, ElementDataType.string.name());
      jsonSchema.put(JSON_SCHEMA_ELEMENT_TYPE, ElementDataType.rowpath.name());
    } else if (dataType == ElementDataType.string) {
      jsonSchema.put(JSON_SCHEMA_TYPE, ElementDataType.string.name());
      if (!c.getElementType().equals(dataType.name())) {
        jsonSchema.put(JSON_SCHEMA_ELEMENT_TYPE, c.getElementType());
      }
    } else {
      throw new IllegalStateException("unexpected alternative ElementDataType");
    }
  }

  @Override
  public int compareTo(ColumnDefinition another) {
    return this.getElementKey().compareTo(another.getElementKey());
  }

}