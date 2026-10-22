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
 *                              Returns { upserted: <n>, skippedIds: [<id>, ...] }.
 *
 * COMPLETED TASKS LIVE IN A SEPARATE TAB
 * ----------------------------------------------------------------------------
 *   Tasks whose Progress is "Completed" are stored in the "Completed Tasks"
 *   tab (same 21 headers); all other tasks live in the "Tasks" tab.
 *   Completing a task MOVES its row to "Completed Tasks"; reopening it
 *   (Progress back to "In Progress" / "Not Started") moves it back to
 *   "Tasks". `snapshot` returns the union of both tabs, and `delete`
 *   removes the row from whichever tab holds it, so the app always sees
 *   one combined task list while the sheet keeps completed work separate.
 *
 * SUMMARY TAB
 * ----------------------------------------------------------------------------
 *   The script also maintains a read-only "Summary" tab (totals, overdue
 *   count, money totals, vendor-wise dues). It is refreshed after every
 *   write and on every snapshot, but only rewritten when the numbers
 *   actually change. Amount due rule: for tasks whose Payment Status is
 *   not "Paid", due = Actual Cost when given, otherwise Estimated Cost.
 *   Edit the task tabs, never the Summary tab — it will be regenerated.
 *
 * DATA FORMATS
 * ----------------------------------------------------------------------------
 *   Dates (Deadline, Next Follow-up, Delivery Date):
 *                              "yyyy-MM-dd"            e.g. 2026-10-20
 *   Timestamps (Task Added, Task Started, Task Completed, Last Updated):
 *                              "yyyy-MM-dd'T'HH:mm:ss" e.g. 2026-10-20T14:30:00
 *                              (a trailing timezone offset like +05:30 or Z
 *                              is also accepted for robustness)
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
var COMPLETED_TAB = 'Completed Tasks';
var SUMMARY_TAB = 'Summary';
var VERSION = 3;
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
// yyyy-MM-dd'T'HH:mm:ss, with optional timezone offset (old app versions sent one)
var TIMESTAMP_RE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}([+-]\d{2}:?\d{2}|Z)?$/;

// ---------------------------------------------------------------------------
// Entry points
// ---------------------------------------------------------------------------

function doPost(e) {
  try {
    var req = parseRequest_(e);

    var auth = checkAuth_(req.token);
    if (!auth.ok) return respond_(auth);

    var sheet = getTasksSheet_();
    var completedSheet = getCompletedSheet_();
    var action = String(req.action || '');
    var payload = req.payload || {};
    var res;

    if (action === 'ping') {
      res = handlePing_(sheet, completedSheet);
    } else if (action === 'snapshot') {
      res = handleSnapshot_(sheet, completedSheet);
    } else if (action === 'upsert') {
      res = handleUpsert_(sheet, completedSheet, payload);
    } else if (action === 'delete') {
      res = handleDelete_(sheet, completedSheet, payload);
    } else if (action === 'batchUpsert') {
      res = handleBatchUpsert_(sheet, completedSheet, payload);
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
  // Trim both sides: copy-paste from chats/emails often adds invisible
  // leading/trailing whitespace, which would otherwise cause a mismatch.
  var want = String(expected).replace(/^\s+|\s+$/g, '');
  var got = String(providedToken === undefined || providedToken === null ? '' : providedToken).replace(/^\s+|\s+$/g, '');
  if (!safeEqual_(want, got)) {
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

/** Return the named tab, creating it and its header row if missing. */
function getTab_(name) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(name);
  if (!sheet) {
    sheet = ss.insertSheet(name);
  }
  if (sheet.getLastRow() === 0) {
    // Completely empty tab: write the canonical headers. An existing,
    // non-empty header row is left untouched (never renamed/reordered).
    sheet.getRange(1, 1, 1, HEADERS.length).setValues([HEADERS]);
    sheet.setFrozenRows(1);
  }
  return sheet;
}

/** The "Tasks" tab: pending / in-progress tasks. */
function getTasksSheet_() {
  return getTab_(SHEET_TAB);
}

/** The "Completed Tasks" tab: same 21 headers, completed tasks only. */
function getCompletedSheet_() {
  return getTab_(COMPLETED_TAB);
}

/** The tab a task belongs in, based on its Progress value. */
function targetSheetFor_(task, tasksSheet, completedSheet) {
  return str_(task['Progress']) === 'Completed' ? completedSheet : tasksSheet;
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

function handlePing_(sheet, completedSheet) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var pendingCount = Math.max(0, sheet.getLastRow() - 1);
  var completedCount = Math.max(0, completedSheet.getLastRow() - 1);
  return ok_({
    version: VERSION,
    sheetTitle: ss.getName(),
    taskCount: pendingCount + completedCount,
    completedCount: completedCount
  });
}

/** All task objects stored in one tab (fully-blank rows skipped). */
function readTasksFrom_(sheet) {
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
  return tasks;
}

function handleSnapshot_(sheet, completedSheet) {
  // Union of both tabs. If an ID somehow exists in both, the Completed
  // copy wins — that tab is the task's current home once it is completed.
  var byId = {};
  var noId = [];
  var order = [];
  var all = readTasksFrom_(sheet).concat(readTasksFrom_(completedSheet));
  for (var i = 0; i < all.length; i++) {
    var id = str_(all[i]['ID']);
    if (!id) {
      noId.push(all[i]);
    } else {
      if (!(id in byId)) order.push(id);
      byId[id] = all[i];
    }
  }
  var tasks = [];
  for (var k = 0; k < order.length; k++) tasks.push(byId[order[k]]);
  for (var n = 0; n < noId.length; n++) tasks.push(noId[n]);
  safeRefreshSummary_(tasks);
  return ok_({ tasks: tasks });
}

/**
 * Write a task into its home tab (by Progress) and remove it from the
 * other tab if it was there — this is what moves a row when a task is
 * completed or reopened. Caller must hold the script lock.
 */
function upsertTask_(tasksSheet, completedSheet, task) {
  var id = str_(task['ID']);
  var target = targetSheetFor_(task, tasksSheet, completedSheet);
  var other = target === tasksSheet ? completedSheet : tasksSheet;

  var otherRow = findRowById_(other, id);
  if (otherRow > 0) other.deleteRow(otherRow);

  var rowValues = taskToRow_(task);
  var row = findRowById_(target, id);
  if (row > 0) {
    target.getRange(row, 1, 1, HEADERS.length).setValues([rowValues]);
  } else {
    target.appendRow(rowValues);
  }
}

/** Delete one row by ID from a single tab. Returns true when removed. Caller holds the lock. */
function deleteFrom_(sheet, id) {
  var row = findRowById_(sheet, id);
  if (row > 0) {
    sheet.deleteRow(row); // exactly one row; never a range, never a wipe
    return true;
  }
  return false;
}

function handleUpsert_(sheet, completedSheet, payload) {
  var task = (payload && payload.task) || {};
  var validationError = validateTask_(task);
  if (validationError) return fail_('Validation failed: ' + validationError);

  var lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    upsertTask_(sheet, completedSheet, task);
    SpreadsheetApp.flush();
    safeRefreshSummaryFromSheets_(sheet, completedSheet);
    return ok_({ id: str_(task['ID']) });
  } finally {
    lock.releaseLock();
  }
}

function handleDelete_(sheet, completedSheet, payload) {
  var id = str_(payload && payload.id);
  if (!id) return fail_('Validation failed: id is required');

  var lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    // A task lives in exactly one tab, but check both so a delete always lands.
    var deleted = deleteFrom_(sheet, id);
    if (deleteFrom_(completedSheet, id)) deleted = true;
    SpreadsheetApp.flush();
    safeRefreshSummaryFromSheets_(sheet, completedSheet);
    return ok_({ deleted: deleted });
  } finally {
    lock.releaseLock();
  }
}

function handleBatchUpsert_(sheet, completedSheet, payload) {
  var tasks = payload && payload.tasks;
  if (!Array.isArray(tasks)) return fail_('Validation failed: tasks must be an array');
  if (tasks.length > MAX_BATCH) {
    return fail_('Validation failed: max ' + MAX_BATCH + ' tasks per call');
  }

  var lock = LockService.getScriptLock();
  lock.waitLock(30000);
  try {
    var upserted = 0;
    var skippedIds = [];

    for (var i = 0; i < tasks.length; i++) {
      var task = tasks[i] || {};
      var tid = str_(task['ID']);
      if (validateTask_(task)) {
        if (tid) skippedIds.push(tid); // report skipped so the app can retry
        continue;
      }
      upsertTask_(sheet, completedSheet, task);
      upserted++;
    }

    SpreadsheetApp.flush();
    safeRefreshSummaryFromSheets_(sheet, completedSheet);
    return ok_({ upserted: upserted, skippedIds: skippedIds });
  } finally {
    lock.releaseLock();
  }
}

// ---------------------------------------------------------------------------
// Summary tab (auto-maintained, read-only for humans)
// ---------------------------------------------------------------------------

function getSummarySheet_() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(SUMMARY_TAB);
  if (!sheet) {
    sheet = ss.insertSheet(SUMMARY_TAB);
  }
  return sheet;
}

/** "₹1,200" / "1200.50" / "" -> number (0 when unparseable). */
function parseMoney_(v) {
  var s = str_(v).replace(/[^0-9.]/g, '');
  if (!s) return 0;
  var n = parseFloat(s);
  return isNaN(n) ? 0 : n;
}

function round2_(n) {
  return Math.round(n * 100) / 100;
}

/**
 * Aggregates over the combined task list (both tabs).
 * Amount due rule (documented on the tab itself): for tasks whose
 * Payment Status is not "Paid", due = Actual Cost when given,
 * otherwise Estimated Cost.
 */
function computeSummary_(tasks) {
  var todayStr = Utilities.formatDate(new Date(), Session.getScriptTimeZone(), 'yyyy-MM-dd');
  var s = {
    total: 0, notStarted: 0, inProgress: 0, completed: 0,
    overdue: 0, dueToday: 0,
    totalEstimated: 0, totalActual: 0, totalDue: 0, pendingPaymentTasks: 0,
    vendors: []
  };
  var byVendor = {};
  for (var i = 0; i < tasks.length; i++) {
    var t = tasks[i];
    s.total++;
    var progress = str_(t['Progress']);
    var isCompleted = progress === 'Completed';
    if (isCompleted) s.completed++;
    else if (progress === 'In Progress') s.inProgress++;
    else s.notStarted++;

    var deadline = str_(t['Deadline']);
    if (!isCompleted && DATE_RE.test(deadline)) {
      if (deadline < todayStr) s.overdue++;
      else if (deadline === todayStr) s.dueToday++;
    }

    var est = parseMoney_(t['Estimated Cost (INR)']);
    var act = parseMoney_(t['Actual Cost (INR)']);
    s.totalEstimated += est;
    s.totalActual += act;

    var paid = str_(t['Payment Status']).replace(/^\s+|\s+$/g, '').toLowerCase() === 'paid';
    if (!paid) {
      var due = act > 0 ? act : est;
      if (due > 0) {
        s.totalDue += due;
        s.pendingPaymentTasks++;
        var vname = str_(t['Vendor / Craftsman']) || '(No vendor)';
        if (!(vname in byVendor)) byVendor[vname] = { name: vname, count: 0, due: 0 };
        byVendor[vname].count++;
        byVendor[vname].due += due;
      }
    }
  }
  s.totalEstimated = round2_(s.totalEstimated);
  s.totalActual = round2_(s.totalActual);
  s.totalDue = round2_(s.totalDue);
  var names = Object.keys(byVendor);
  for (var v = 0; v < names.length; v++) {
    var entry = byVendor[names[v]];
    entry.due = round2_(entry.due);
    s.vendors.push(entry);
  }
  s.vendors.sort(function (a, b) {
    if (b.due !== a.due) return b.due - a.due;
    return a.name < b.name ? -1 : (a.name > b.name ? 1 : 0);
  });
  return s;
}

/** The Summary tab as a 3-column grid of rows. */
function buildSummaryRows_(s) {
  var updated = Utilities.formatDate(new Date(), Session.getScriptTimeZone(), "yyyy-MM-dd'T'HH:mm:ss");
  var rows = [
    ['Task Tracker Pro — Summary', '', ''],
    ['Last updated', updated, ''],
    ['', '', ''],
    ['Overview', '', ''],
    ['Total tasks', s.total, ''],
    ['Pending tasks', s.total - s.completed, ''],
    ['Not Started', s.notStarted, ''],
    ['In Progress', s.inProgress, ''],
    ['Completed', s.completed, ''],
    ['Overdue', s.overdue, ''],
    ['Due today', s.dueToday, ''],
    ['', '', ''],
    ['Money (INR)', '', ''],
    ['Total Estimated Cost', s.totalEstimated, ''],
    ['Total Actual Cost', s.totalActual, ''],
    ['Amount due (unpaid tasks)', s.totalDue, ''],
    ['Tasks with payment pending', s.pendingPaymentTasks, ''],
    ['', '', ''],
    ['Vendor-wise dues', '', ''],
    ['Vendor', 'Pending tasks', 'Amount due (INR)']
  ];
  for (var i = 0; i < s.vendors.length; i++) {
    rows.push([s.vendors[i].name, s.vendors[i].count, s.vendors[i].due]);
  }
  rows.push(['', '', '']);
  rows.push(['Note', 'Amount due rule: for tasks whose Payment Status is not "Paid", due = Actual Cost if given, else Estimated Cost. This tab is auto-generated — edit the Tasks tabs, not this one.', '']);
  return rows;
}

/**
 * Rewrites the Summary tab only when the aggregated numbers changed
 * (signature kept in Script Properties), so frequent syncs don't churn
 * the sheet. Returns true when it rewrote the tab.
 */
function refreshSummary_(tasks) {
  var s = computeSummary_(tasks);
  var sig = JSON.stringify(s);
  var props = PropertiesService.getScriptProperties();
  if (props.getProperty('SUMMARY_SIG_V1') === sig) return false;
  var sheet = getSummarySheet_();
  var rows = buildSummaryRows_(s);
  sheet.clearContents();
  sheet.getRange(1, 1, rows.length, 3).setValues(rows);
  try {
    sheet.getRange(1, 1).setFontWeight('bold');
    sheet.getRange(4, 1).setFontWeight('bold');
    sheet.getRange(13, 1).setFontWeight('bold');
    sheet.getRange(19, 1).setFontWeight('bold');
    sheet.getRange(20, 1, 1, 3).setFontWeight('bold');
  } catch (fmtErr) {
    // Formatting is cosmetic; never fail the refresh over it.
  }
  props.setProperty('SUMMARY_SIG_V1', sig);
  return true;
}

/** Summary refresh must never break a sync response — failures are swallowed. */
function safeRefreshSummary_(tasks) {
  try {
    refreshSummary_(tasks);
  } catch (err) {
    // ignore
  }
}

function safeRefreshSummaryFromSheets_(tasksSheet, completedSheet) {
  try {
    refreshSummary_(readTasksFrom_(tasksSheet).concat(readTasksFrom_(completedSheet)));
  } catch (err) {
    // ignore
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
