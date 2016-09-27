/*
 * Copyright (C) 2013 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.opendatakit.sync.provider;

import org.opendatakit.aggregate.odktables.rest.TableConstants;
import org.opendatakit.aggregate.odktables.rest.entity.RowFilterScope;

/**
 * Metadata Columns added to the user-defined data tables.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class DataTableColumns implements BaseColumns {

  /**
   * For simplicity, share the exact names with the REST interface to the server.
   */

  // tablename is chosen by user...
  public static final String ID = TableConstants.ID;
  public static final String ROW_ETAG = TableConstants.ROW_ETAG;
  public static final String SYNC_STATE = TableConstants.SYNC_STATE;
  public static final String CONFLICT_TYPE = TableConstants.CONFLICT_TYPE;
  public static final String FILTER_TYPE = TableConstants.FILTER_TYPE;
  public static final String FILTER_VALUE = TableConstants.FILTER_VALUE;

  /**
   * (_savepoint_timestamp, _savepoint_type)
   * are managed by the database layer based upon the AidlDbInterface methods being
   * called to update a record.
   *
   * <p>_savepoint_timestamp is an iso8601-style UTC timestamp with nanosecond resolution.</p>
   * <ul><li>String TableConstants.nanoSecondsFromMillis(Long)</li>
   * <li>Long TableConstants.milliSecondsFromNanos(String)</li></ul>
   * <p>For converting to and from this string representation.</p>
   */
  public static final String SAVEPOINT_TIMESTAMP = TableConstants.SAVEPOINT_TIMESTAMP;
  public static final String SAVEPOINT_TYPE = TableConstants.SAVEPOINT_TYPE;

  /**
   * (_savepoint_creator, _form_id, _locale)
   * are the tuple written and managed by ODK Survey when a record is updated.
   * ODK Tables and other clients need to update these appropriately when the
   * row is modified.
   */
  public static final String SAVEPOINT_CREATOR = TableConstants.SAVEPOINT_CREATOR;
  public static final String FORM_ID = TableConstants.FORM_ID;
  public static final String LOCALE = TableConstants.LOCALE;

  // This column is added to report effective privileges on the data rows
  // Effective privileges are one of "r", "rw" or "rwd" and are determined by
  // the verified user's roles and the status of the table.
  public static final String EFFECTIVE_ACCESS = "_effective_access";

  // These are the default values that will be set to the database in case
  // there is nothing included. This has been a problem when downloading a
  // table from the server.
  public static final String DEFAULT_ROW_ETAG = null;
  public static final String DEFAULT_FILTER_TYPE = RowFilterScope.EMPTY_ROW_FILTER.getType().name();
  public static final String DEFAULT_FILTER_VALUE = RowFilterScope.EMPTY_ROW_FILTER.getValue();
  // the default _savepoint_creator is: PropertiesSingleton.getActiveUser()
  // the default _locale is: PropertiesSingleton.getLocale()

  // This class cannot be instantiated
  private DataTableColumns() {
  }

}
