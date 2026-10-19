/**
 * ============================================================================
 * Task Tracker Pro — Google Apps Script Web App backend
 * ============================================================================
 * Container-bound script. Create it via Extensions → Apps Script inside a NEW,
 * DEDICATED Google Sheet (do NOT reuse an existing personal spreadsheet).
 *
 * The Android app POSTs JSON to the deployed Web App URL (ending in /exec):
 *
 *   Request:  { "token": "<secret>", "action": "<ping|snapshot|upsert|delete|batchUpsert>",
 *               "payload": { ... } }
 *   Response: { "ok": true|false, "error": "<message or empty>", "data": { ... } }
 *
 * Actions
 * -------
 *   ping         (no payload)  Verify token only. Returns
 *                              { version, sheetTitle, taskCount }. Modifies nothing.
 *   snapshot     (no payload)  Returns { tasks: [ {<21 header-named fields>}, ... ] }.
 *                              Fully-blank rows are skipped. Trailing blank
 *                              columns beyond the 21 headers are ignored.
 *   upsert       payload { task: {<21 fields>} }
 *                              Update the row whose column A (ID) matches, else
 *                              append. ID and "Task Name" are required.
 *                              Returns { id }.
 *   delete       payload { id }
 *                              Delete the single row whose column A equals id.
 *                              Returns { deleted: true|false }. Never deletes
 *                              more than one row; never wipes the sheet.
 *   batchUpsert  payload { tasks: [ ... ] }
 *                              Upsert each task (max 500 per call). Tasks that
 *                              fail validation are skipped and NOT counted.
 *                              Returns { upserted: <n> }.
 *
 * DATA FORMATS
 * ----------------------------------------------------------------------------
 *   Dates (Deadline, Next Follow-up, Delivery Date):
 *                              "yyyy-MM-dd"            e.g. 2026-10-20
 *   Timestamps (Task Added, Task Started, Task Completed, Last Updated):
 *                              "yyyy-MM-dd'T'HH:mm:ss" e.g. 2026-10-20T14:30:00
 *   Progress:                  one of Not Started | In Progress | Completed
 *                              (empty is also accepted)
 *   Priority:                  one of Low | Medium | High
 *                              (empty is also accepted)
 *   Payment Status:            free text, e.g. Pending | Paid | Partial
 *
 * All values are written to cells as plain strings (numbers and dates are
 * coerced to text) to avoid Google Sheets type coercion surprises.
 *
 * SECURITY
 * ----------------------------------------------------------------------------
 *   - The secret lives ONLY in Script Properties under the key SECRET_TOKEN
 *     (Apps Script → Project Settings → Script Properties). It is never
 *     hard-coded here, never logged, and never included in any response.
 *   - If SECRET_TOKEN is not set, every action fails with
 *     "Server not configured: SECRET_TOKEN missing".
 *   - Tokens are compared in constant time. On mismatch the response is
 *     exactly { ok:false, error:"Unauthorized" } with no further detail.
 *   - All inputs are validated; everything is wrapped in try/catch and
 *     exceptions return a safe message — stack traces are never leaked.
 *
 * SHEET SCHEMA — the 21 canonical headers, exact order, tab "Tasks"
 * ----------------------------------------------------------------------------
 *   ID | Task Name | Type | Progress | Priority | Deadline |
 *   Vendor / Craftsman | Contact Number | Estimated Cost (INR) |
 *   Actual Cost (INR) | Next Follow-up | Delivery Date | Payment Status |
 *   Link | Task Focus | Value Add | Notes | Task Added | Task Started |
 *   Task Completed | Last Updated
 *
 * The tab and its header row are created automatically if missing. Existing
 * headers are never renamed or reordered by this script.
 */

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

var SHEET_TAB = 'Tasks';
var VERSION = 1;
var MAX_BATCH = 500;
var TOKEN_PROPERTY = 'SECRET_TOKEN';

var HEADERS = [
  'ID',
  'Task Name',
  'Type',
  'Progress',
  'Priority',
  'Deadline',
  'Vendor / Craftsman',
  'Contact Number',
  'Estimated Cost (INR)',
  'Actual Cost (INR)',
  'Next Follow-up',
  'Delivery Date',
  'Payment Status',
  'Link',
  'Task Focus',
  'Value Add',
  'Notes',
  'Task Added',
  'Task Started',
  'Task Completed',
  'Last Updated'
];

var VALID_PROGRESS = ['Not Started', 'In Progress', 'Completed'];
var VALID_PRIORITY = ['Low', 'Medium', 'High'];
var DATE_RE = /^\d{4}-\d{2}-\d{2}$/;                 // yyyy-MM-dd
var TIMESTAMP_RE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/; // yyyy-MM-dd'T'HH:mm:ss

// ---------------------------------------------------------------------------
// Entry points
// ---------------------------------------------------------------------------

function doPost(e) {
  try {
    var req = parseRequest_(e);

    var auth = checkAuth_(req.token);
    if (!auth.ok) return respond_(auth);

    var sheet = getTasksSheet_();
    var action = String(req.action || '');
    var payload = req.payload || {};
    var res;

    if (action === 'ping') {
      res = handlePing_(sheet);
    } else if (action === 'snapshot') {
      res = handleSnapshot_(sheet);
    } else if (action === 'upsert') {
      res = handleUpsert_(sheet, payload);
    } else if (action === 'delete') {
      res = handleDelete_(sheet, payload);
    } else if (action === 'batchUpsert') {
      res = handleBatchUpsert_(sheet, payload);
    } else {
      res = fail_('Unknown action');
    }
    return respond_(res);
  } catch (err) {
    // Never leak stack traces or internals.
    return respond_(fail_('Request failed'));
  }
}

function doGet() {
  return respond_(fail_('Use POST with a JSON body'));
}

// ---------------------------------------------------------------------------
// Request parsing & auth
// ---------------------------------------------------------------------------

function parseRequest_(e) {
  var contents = e && e.postData && e.postData.contents;
  if (!contents) throw new Error('empty');
  var body;
  try {
    body = JSON.parse(contents);
  } catch (parseErr) {
    throw new Error('bad-json');
  }
  return {
    token: body.token,
    action: body.action,
    payload: body.payload || {}
  };
}

function checkAuth_(providedToken) {
  var expected = PropertiesService.getScriptProperties().getProperty(TOKEN_PROPERTY);
  if (!expected) {
    return fail_('Server not configured: SECRET_TOKEN missing');
  }
  if (!safeEqual_(expected, providedToken)) {
    return fail_('Unauthorized');
  }
  return ok_({});
}

/** Constant-time string comparison to avoid timing side channels. */
function safeEqual_(a, b) {
  a = String(a === undefined || a === null ? '' : a);
  b = String(b === undefined || b === null ? '' : b);
  var diff = a.length ^ b.length;
  var len = Math.max(a.length, b.length);
  for (var i = 0; i < len; i++) {
    var ca = i < a.length ? a.charCodeAt(i) : 0;
    var cb = i < b.length ? b.charCodeAt(i) : 0;
    diff |= (ca ^ cb);
  }
  return diff === 0;
}

// ---------------------------------------------------------------------------
// Sheet access
// ---------------------------------------------------------------------------

/** Return the "Tasks" tab, creating it and its header row if missing. */
function getTasksSheet_() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(SHEET_TAB);
  if (!sheet) {
    sheet = ss.insertSheet(SHEET_TAB);
  }
  if (sheet.getLastRow() === 0) {
    // Completely empty tab: write the canonical headers. An existing,
    // non-empty header row is left untouched (never renamed/reordered).
    sheet.getRange(1, 1, 1, HEADERS.length).setValues([HEADERS]);
    sheet.setFrozenRows(1);
  }
  return sheet;
}

/** Map of task ID (column A) -> 1-based row number, for existing data rows. */
function buildIdIndex_(sheet) {
  var index = {};
  var lastRow = sheet.getLastRow();
  if (lastRow < 2) return index;
  var ids = sheet.getRange(2, 1, lastRow - 1, 1).getValues();
  for (var i = 0; i < ids.length; i++) {
    var id = toCellString_(ids[i][0]);
    if (id && !(id in index)) {
      index[id] = i + 2; // first occurrence wins
    }
  }
  return index;
}

/** Row number (1-based) of the first data row whose column A equals id, else 0. */
function findRowById_(sheet, id) {
  var index = buildIdIndex_(sheet);
  return index[id] || 0;
}

// ---------------------------------------------------------------------------
// Action handlers
// ---------------------------------------------------------------------------

function handlePing_(sheet) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var taskCount = Math.max(0, sheet.getLastRow() - 1);
  return ok_({
    version: VERSION,
    sheetTitle: ss.getName(),
    taskCount: taskCount
  });
}

function handleSnapshot_(sheet) {
  var tasks = [];
  var lastRow = sheet.getLastRow();
  if (lastRow > 1) {
    var values = sheet.getRange(2, 1, lastRow - 1, HEADERS.length).getValues();
    for (var r = 0; r < values.length; r++) {
      var obj = {};
      var allBlank = true;
      for (var c = 0; c < HEADERS.length; c++) {
        var v = toCellString_(values[r][c]);
        obj[HEADERS[c]] = v;
        if (v !== '') allBlank = false;
      }
      if (!allBlank) tasks.push(obj); // skip fully-blank rows
    }
  }
  return ok_({ tasks: tasks });
}

function handleUpsert_(sheet, payload) {
  var task = (payload && payload.task) || {};
  var validationError = validateTask_(task);
  if (validationError) return fail_('Validation failed: ' + validationError);

  var id = str_(task['ID']);
  var rowValues = taskToRow_(task);

  var lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    var row = findRowById_(sheet, id);
    if (row > 0) {
      sheet.getRange(row, 1, 1, HEADERS.length).setValues([rowValues]);
    } else {
      sheet.appendRow(rowValues);
    }
    SpreadsheetApp.flush();
    return ok_({ id: id });
  } finally {
    lock.releaseLock();
  }
}

function handleDelete_(sheet, payload) {
  var id = str_(payload && payload.id);
  if (!id) return fail_('Validation failed: id is required');

  var lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    var row = findRowById_(sheet, id);
    if (row > 0) {
      sheet.deleteRow(row); // exactly one row; never a range, never a wipe
      SpreadsheetApp.flush();
      return ok_({ deleted: true });
    }
    return ok_({ deleted: false });
  } finally {
    lock.releaseLock();
  }
}

function handleBatchUpsert_(sheet, payload) {
  var tasks = payload && payload.tasks;
  if (!Array.isArray(tasks)) return fail_('Validation failed: tasks must be an array');
  if (tasks.length > MAX_BATCH) {
    return fail_('Validation failed: max ' + MAX_BATCH + ' tasks per call');
  }

  var lock = LockService.getScriptLock();
  lock.waitLock(30000);
  try {
    var idIndex = buildIdIndex_(sheet);
    var updates = []; // { row: n, values: [...] }
    var appends = []; // [ [...], [...] ]
    var upserted = 0;

    for (var i = 0; i < tasks.length; i++) {
      var task = tasks[i] || {};
      if (validateTask_(task)) continue; // skip invalid tasks; not counted
      var id = str_(task['ID']);
      var rowValues = taskToRow_(task);
      var row = idIndex[id] || 0;
      if (row > 0) {
        updates.push({ row: row, values: rowValues });
      } else {
        appends.push(rowValues);
        // Reserve the future row so duplicate IDs within this batch
        // update the same pending row instead of creating duplicates.
        idIndex[id] = sheet.getLastRow() + appends.length;
      }
      upserted++;
    }

    for (var u = 0; u < updates.length; u++) {
      sheet.getRange(updates[u].row, 1, 1, HEADERS.length).setValues([updates[u].values]);
    }
    if (appends.length > 0) {
      var startRow = sheet.getLastRow() + 1;
      sheet.getRange(startRow, 1, appends.length, HEADERS.length).setValues(appends);
    }
    SpreadsheetApp.flush();
    return ok_({ upserted: upserted });
  } finally {
    lock.releaseLock();
  }
}

// ---------------------------------------------------------------------------
// Validation & coercion
// ---------------------------------------------------------------------------

/** Returns an error string, or null when the task is valid. */
function validateTask_(task) {
  if (!task || typeof task !== 'object') return 'task must be an object';
  var id = str_(task['ID']);
  if (!id) return 'ID is required';
  var name = str_(task['Task Name']);
  if (!name) return 'Task Name is required';

  var progress = str_(task['Progress']);
  if (progress && VALID_PROGRESS.indexOf(progress) === -1) {
    return 'Progress must be one of: ' + VALID_PROGRESS.join(', ');
  }
  var priority = str_(task['Priority']);
  if (priority && VALID_PRIORITY.indexOf(priority) === -1) {
    return 'Priority must be one of: ' + VALID_PRIORITY.join(', ');
  }
  var dateFields = ['Deadline', 'Next Follow-up', 'Delivery Date'];
  for (var d = 0; d < dateFields.length; d++) {
    var dv = str_(task[dateFields[d]]);
    if (dv && !DATE_RE.test(dv)) {
      return dateFields[d] + ' must use format yyyy-MM-dd';
    }
  }
  var tsFields = ['Task Added', 'Task Started', 'Task Completed', 'Last Updated'];
  for (var t = 0; t < tsFields.length; t++) {
    var tv = str_(task[tsFields[t]]);
    if (tv && !TIMESTAMP_RE.test(tv)) {
      return tsFields[t] + " must use format yyyy-MM-dd'T'HH:mm:ss";
    }
  }
  return null;
}

/** Task object -> 21-element row array in canonical header order, all strings. */
function taskToRow_(task) {
  var row = [];
  for (var c = 0; c < HEADERS.length; c++) {
    row.push(str_(task[HEADERS[c]]));
  }
  return row;
}

/** Coerce any value to a string for writing to a cell. */
function str_(v) {
  if (v === null || v === undefined) return '';
  return String(v);
}

/** Coerce a cell value read from the sheet to a string (Dates -> timestamp text). */
function toCellString_(v) {
  if (v === null || v === undefined || v === '') return '';
  if (Object.prototype.toString.call(v) === '[object Date]') {
    return Utilities.formatDate(v, Session.getScriptTimeZone(), "yyyy-MM-dd'T'HH:mm:ss");
  }
  return String(v);
}

// ---------------------------------------------------------------------------
// Response helpers
// ---------------------------------------------------------------------------

function ok_(data) {
  return { ok: true, error: '', data: data || {} };
}

function fail_(message) {
  return { ok: false, error: String(message), data: {} };
}

function respond_(obj) {
  if (typeof obj.error === 'undefined' || obj.error === null) obj.error = '';
  if (typeof obj.data === 'undefined' || obj.data === null) obj.data = {};
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
