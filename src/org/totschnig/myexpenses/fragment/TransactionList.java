/*   This file is part of My Expenses.
 *   My Expenses is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   My Expenses is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with My Expenses.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.totschnig.myexpenses.fragment;

import static org.totschnig.myexpenses.provider.DatabaseConstants.*;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.activity.CommonCommands;
import org.totschnig.myexpenses.activity.MyExpenses;
import org.totschnig.myexpenses.dialog.EditTextDialog;
import org.totschnig.myexpenses.dialog.ProgressDialogFragment;
import org.totschnig.myexpenses.dialog.SelectFromCursorDialogFragment;
import org.totschnig.myexpenses.dialog.TransactionDetailFragment;
import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.model.Money;
import org.totschnig.myexpenses.model.ContribFeature.Feature;
import org.totschnig.myexpenses.provider.DbUtils;
import org.totschnig.myexpenses.provider.TransactionProvider;
import org.totschnig.myexpenses.util.Utils;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.emilsjolander.components.stickylistheaders.StickyListHeadersAdapter;
import com.emilsjolander.components.stickylistheaders.StickyListHeadersListView;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Toast;

//TODO: consider moving to ListFragment
public class TransactionList extends SherlockFragment implements
    LoaderManager.LoaderCallbacks<Cursor> {
  private static final int TRANSACTION_CURSOR = 0;
  private static final int SUM_CURSOR = 1;
  private static final int GROUPING_CURSOR = 2;
  long mAccountId;
  SimpleCursorAdapter mAdapter;
  private int colorExpense;
  private int colorIncome;
  private AccountObserver aObserver;
  private Account mAccount;
  private TextView balanceTv;
  private View bottomLine;
  private boolean hasItems;
  private long transactionSum = 0;
  private Cursor mTransactionsCursor, mGroupingCursor;
  DateFormat headerDateFormat, itemDateFormat;
  String headerPrefix;
  private StickyListHeadersListView mListView;
  private LoaderManager mManager;

  int columnIndexDate, columnIndexYear, columnIndexMonth, columnIndexWeek, columnIndexDay,
    columnIndexAmount, columnIndexLabelSub, columnIndexComment, columnIndexPayee,
    columnIndexGroupYear, columnIndexGroupSecond,
    columnIndexGroupSumIncome, columnIndexGroupSumExpense, columnIndexGroupSumTransfer;
  boolean indexesCalculated, indexesGroupingCalculated = false;

  public static TransactionList newInstance(long accountId) {
    
    TransactionList pageFragment = new TransactionList();
    Bundle bundle = new Bundle();
    bundle.putLong("account_id", accountId);
    pageFragment.setArguments(bundle);
    return pageFragment;
  }
  @Override
  public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setHasOptionsMenu(true);

      mAccountId = getArguments().getLong("account_id");
      mAccount = Account.getInstanceFromDb(getArguments().getLong("account_id"));
      aObserver = new AccountObserver(new Handler());
      ContentResolver cr= getSherlockActivity().getContentResolver();
      //when account has changed, we might have
      //1) to refresh the list (currency has changed),
      //2) update current balance(opening balance has changed),
      //3) update the bottombarcolor (color has changed)
      //4) refetch grouping cursor (grouping has changed)
      cr.registerContentObserver(
          TransactionProvider.ACCOUNTS_URI,
          true,aObserver);
  }
  private void setAdapter() {
    Context ctx = getSherlockActivity();
    // Create an array to specify the fields we want to display in the list
    String[] from = new String[]{KEY_LABEL_MAIN,KEY_DATE,KEY_AMOUNT};

    // and an array of the fields we want to bind those fields to 
    int[] to = new int[]{R.id.category,R.id.date,R.id.amount};
    mAdapter = new MyGroupedAdapter(ctx, R.layout.expense_row, null, from, to,0);
    mListView.setAdapter(mAdapter);
  }
  private void setGrouping() {
    switch (mAccount.grouping) {
    case DAY:
      itemDateFormat = new SimpleDateFormat("HH:mm");
      break;
    case MONTH:
      itemDateFormat = new SimpleDateFormat("dd");
      break;
    case WEEK:
      itemDateFormat = new SimpleDateFormat("EEE");
      break;
    case YEAR:
    case NONE:
      itemDateFormat = new SimpleDateFormat("dd.MM");
    }
    mGroupingCursor = null;
    if (mManager.getLoader(GROUPING_CURSOR) != null && !mManager.getLoader(GROUPING_CURSOR).isReset())
      mManager.restartLoader(GROUPING_CURSOR, null, this);
    else
      mManager.initLoader(GROUPING_CURSOR, null, this);
  }
  @Override
  public void onDestroy() {
    super.onDestroy();
    try {
      ContentResolver cr = getSherlockActivity().getContentResolver();
      cr.unregisterContentObserver(aObserver);
    } catch (IllegalStateException ise) {
        // Do Nothing.  Observer has already been unregistered.
    }
  }
  @Override
  public void onPrepareOptionsMenu(Menu menu) {
    if (isVisible())
      menu.findItem(R.id.RESET_ACCOUNT_COMMAND).setVisible(hasItems);
  }

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v,
      ContextMenuInfo menuInfo) {
    AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
    super.onCreateContextMenu(menu, v, menuInfo);
    mTransactionsCursor.moveToPosition(info.position);
    menu.add(0, R.id.DELETE_COMMAND, 0, R.string.menu_delete);
    //templates for splits is not yet implemented
    if (! SPLIT_CATID.equals(DbUtils.getLongOrNull(mTransactionsCursor, KEY_CATID)))
      menu.add(0, R.id.CREATE_TEMPLATE_COMMAND, 0, R.string.menu_create_template);
    menu.add(0, R.id.CLONE_TRANSACTION_COMMAND, 0, R.string.menu_clone_transaction);
    //move transaction is disabled for transfers,
    //TODO we also would need to check for splits with transfer parts
    if (((MyExpenses) getSherlockActivity()).getCursor(MyExpenses.ACCOUNTS_CURSOR,null).getCount() > 1 &&
        DbUtils.getLongOrNull(mTransactionsCursor, KEY_TRANSFER_PEER) == null) {
      menu.add(0,R.id.MOVE_TRANSACTION_COMMAND,0,R.string.menu_move_transaction);
    }
  }


  @Override  
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    final MyExpenses ctx = (MyExpenses) getSherlockActivity();
    mManager = getLoaderManager();
    setGrouping();
    Resources.Theme theme = ctx.getTheme();
    TypedValue color = new TypedValue();
    theme.resolveAttribute(R.attr.colorExpense, color, true);
    colorExpense = color.data;
    theme.resolveAttribute(R.attr.colorIncome,color, true);
    colorIncome = color.data;
    
    View v = inflater.inflate(R.layout.expenses_list, null, false);
    //work around the problem that the view pager does not display its background correclty with Sherlock
    if (Build.VERSION.SDK_INT < 11) {
      v.setBackgroundColor(ctx.getResources().getColor(
          MyApplication.getThemeId() == R.style.ThemeLight ? android.R.color.white : android.R.color.black));
    }
    balanceTv = (TextView) v.findViewById(R.id.end);
    bottomLine = v.findViewById(R.id.BottomLine);
    updateColor();
    mListView = (StickyListHeadersListView) v.findViewById(R.id.list);
    setAdapter();
    //mListView.setOnHeaderClickListener(this);
    mManager.initLoader(GROUPING_CURSOR, null, this);
    mManager.initLoader(TRANSACTION_CURSOR, null, this);
    mManager.initLoader(SUM_CURSOR, null, this);
    // Now create a simple cursor adapter and set it to display

    mListView.setEmptyView(v.findViewById(R.id.empty));
    mListView.setOnItemClickListener(new OnItemClickListener()
    {
         @Override
         public void onItemClick(AdapterView<?> a, View v,int position, long id)
         {
           TransactionDetailFragment.newInstance(id)
           .show(ctx.getSupportFragmentManager(), "TRANSACTION_DETAIL");
         }
    });
    registerForContextMenu(mListView);
    return v;
  }

  @Override
  public boolean onContextItemSelected(android.view.MenuItem item) {
    //http://stackoverflow.com/questions/9753213/wrong-fragment-in-viewpager-receives-oncontextitemselected-call
    if (!getUserVisibleHint())
      return false;
    MyExpenses ctx = (MyExpenses) getActivity();
    AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
    Bundle args;
    switch(item.getItemId()) {
    case R.id.DELETE_COMMAND:
      if (checkSplitPartTransfer(info.position)) {
        FragmentManager fm = ctx.getSupportFragmentManager();
        fm.beginTransaction()
          .add(TaskExecutionFragment.newInstance(TaskExecutionFragment.TASK_DELETE_TRANSACTION,info.id), "DELETE_TASK")
          .add(ProgressDialogFragment.newInstance(R.string.progress_dialog_deleting),"PROGRESS")
          .commit();
      }
      return true;
    case R.id.CLONE_TRANSACTION_COMMAND:
      if (MyApplication.getInstance().isContribEnabled) {
        ctx.contribFeatureCalled(Feature.CLONE_TRANSACTION, info.id);
      }
      else {
        CommonCommands.showContribDialog(ctx,Feature.CLONE_TRANSACTION, info.id);
      }
      return true;
    case R.id.MOVE_TRANSACTION_COMMAND:
      args = new Bundle();
      args.putInt("id", R.id.MOVE_TRANSACTION_COMMAND);
      args.putString("dialogTitle",getString(R.string.dialog_title_select_account));
      //args.putString("selection",KEY_ROWID + " != " + mCurrentAccount.id);
      args.putString("column", KEY_LABEL);
      args.putLong("contextTransactionId",info.id);
      args.putInt("cursorId", MyExpenses.ACCOUNTS_OTHER_CURSOR);
      SelectFromCursorDialogFragment.newInstance(args)
        .show(ctx.getSupportFragmentManager(), "SELECT_ACCOUNT");
      return true;
    case R.id.CREATE_TEMPLATE_COMMAND:
      args = new Bundle();
      args.putLong("transactionId", info.id);
      args.putString("dialogTitle", getString(R.string.dialog_title_template_title));
      EditTextDialog.newInstance(args).show(ctx.getSupportFragmentManager(), "TEMPLATE_TITLE");
      return true;
    }
    return super.onContextItemSelected(item);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle arg1) {
    CursorLoader cursorLoader = null;
    switch(id) {
    case TRANSACTION_CURSOR:
      cursorLoader = new CursorLoader(getSherlockActivity(),
          TransactionProvider.TRANSACTIONS_URI, null, "account_id = ? AND parent_id is null",
          new String[] { String.valueOf(mAccountId) }, null);
      break;
    case SUM_CURSOR:
      cursorLoader = new CursorLoader(getSherlockActivity(),
          TransactionProvider.TRANSACTIONS_URI, new String[] {"sum(" + KEY_AMOUNT + ")"}, "account_id = ? AND parent_id is null",
          new String[] { String.valueOf(mAccountId) }, null);
      break;
    case GROUPING_CURSOR:
      cursorLoader = new CursorLoader(getSherlockActivity(),
          TransactionProvider.TRANSACTIONS_URI.buildUpon().appendPath("groups").appendPath(mAccount.grouping.name()).build(),
          null,"account_id = ?",new String[] { String.valueOf(mAccountId) }, null);
      break;
    }
    return cursorLoader;
  }

  @Override
  public void onLoadFinished(Loader<Cursor> arg0, Cursor c) {
    switch(arg0.getId()) {
    case TRANSACTION_CURSOR:
      mTransactionsCursor = c;
      if (!indexesCalculated) {
        columnIndexDate = c.getColumnIndex(KEY_DATE);
        columnIndexYear = c.getColumnIndex("year");
        columnIndexMonth = c.getColumnIndex("month");
        columnIndexWeek = c.getColumnIndex("week");
        columnIndexDay  = c.getColumnIndex("day");
        columnIndexAmount = c.getColumnIndex(KEY_AMOUNT);
        columnIndexLabelSub = c.getColumnIndex(KEY_LABEL_SUB);
        columnIndexComment = c.getColumnIndex(KEY_COMMENT);
        columnIndexPayee = c.getColumnIndex(KEY_PAYEE);
        indexesCalculated = true;
      }
      mAdapter.swapCursor(c);
      hasItems = c.getCount()>0;
      if (isVisible())
        getSherlockActivity().supportInvalidateOptionsMenu();
      break;
    case SUM_CURSOR:
      c.moveToFirst();
      transactionSum = c.getLong(0);
      updateBalance();
      break;
    case GROUPING_CURSOR:
      mGroupingCursor = c;
      //if the transactionscursor has been loaded before the grouping cursor, we need to refresh
      //in order to have accurate grouping values
      if (mAccount.grouping != Account.Grouping.NONE) {
        if (!indexesGroupingCalculated) {
          columnIndexGroupYear = c.getColumnIndex("year");
          columnIndexGroupSecond = c.getColumnIndex("second");
          columnIndexGroupSumIncome = c.getColumnIndex("sum_income");
          columnIndexGroupSumExpense = c.getColumnIndex("sum_expense");
          columnIndexGroupSumTransfer = c.getColumnIndex("sum_transfer");
          indexesGroupingCalculated = true;
        }
        if (mTransactionsCursor != null)
          mAdapter.notifyDataSetChanged();
      }
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> arg0) {
    switch(arg0.getId()) {
    case TRANSACTION_CURSOR:
      mTransactionsCursor = null;
      mAdapter.swapCursor(null);
      hasItems = false;
      if (isVisible())
        getSherlockActivity().supportInvalidateOptionsMenu();
      break;
    case SUM_CURSOR:
      transactionSum=0;
      updateBalance();
      break;
    case GROUPING_CURSOR:
      mGroupingCursor = null;
    }
  }
  class AccountObserver extends ContentObserver {
    public AccountObserver(Handler handler) {
       super(handler);
    }
    public void onChange(boolean selfChange) {
      super.onChange(selfChange);
      updateBalance();
      updateColor();
      if (mAdapter != null) {
        setGrouping();
        mAdapter.notifyDataSetChanged();
      }
    }
  }
  private void updateBalance() {
    if (balanceTv != null)
      balanceTv.setText(Utils.formatCurrency(
          new Money(mAccount.currency,
              mAccount.openingBalance.getAmountMinor() + transactionSum)));
  }
  private void updateColor() {
    if (bottomLine != null)
      bottomLine.setBackgroundColor(mAccount.color);
  }
  private boolean checkSplitPartTransfer(int position) {
    mTransactionsCursor.moveToPosition(position);
    Long transferPeer = DbUtils.getLongOrNull(mTransactionsCursor, KEY_TRANSFER_PEER);
    if (transferPeer != null && DbUtils.hasParent(transferPeer)) {
      Toast.makeText(getActivity(), getString(R.string.warning_splitpartcategory_context), Toast.LENGTH_LONG).show();
      return false;
    }
    return true;
  }
  public class MyGroupedAdapter extends MyAdapter implements StickyListHeadersAdapter {
    LayoutInflater inflater;
    public MyGroupedAdapter(Context context, int layout, Cursor c, String[] from,
        int[] to, int flags) {
      super(context, layout, c, from, to, flags);
      inflater = LayoutInflater.from(getSherlockActivity());
      
    }
    @Override
    public View getHeaderView(int position, View convertView, ViewGroup parent) {
      if (mAccount.grouping.equals(Account.Grouping.NONE))
        return null;
      HeaderViewHolder holder = new HeaderViewHolder();
      if (convertView == null) {
        convertView = inflater.inflate(R.layout.header, parent, false);
        holder.text = (TextView) convertView.findViewById(R.id.text);
        holder.sumExpense = (TextView) convertView.findViewById(R.id.sum_expense);
        holder.sumIncome = (TextView) convertView.findViewById(R.id.sum_income);
        holder.sumTransfer = (TextView) convertView.findViewById(R.id.sum_transfer);
        convertView.setTag(holder);
      } else
        holder = (HeaderViewHolder) convertView.getTag();

      Cursor c = getCursor();
      c.moveToPosition(position);
      String headerText = "";
      int year = c.getInt(columnIndexYear);
      int month = c.getInt(columnIndexMonth);
      int week = c.getInt(columnIndexWeek);
      int day = c.getInt(columnIndexDay);
      Calendar cal = Calendar.getInstance();
      int thisYear = cal.get(Calendar.YEAR);
      int thisDayOfYear = cal.get(Calendar.DAY_OF_YEAR);
      int thisWeekOfYear = cal.get(Calendar.WEEK_OF_YEAR);

      if (mGroupingCursor != null) {
        mGroupingCursor.moveToFirst();
        traverseCursor:
        while (!mGroupingCursor.isAfterLast()) {
          if (mGroupingCursor.getInt(columnIndexGroupYear) == year) {
            switch (mAccount.grouping) {
            case YEAR:
              fillSums(holder,mGroupingCursor);
              headerText = String.valueOf(year);
              break traverseCursor;
            case DAY:
              if (mGroupingCursor.getInt(columnIndexGroupSecond) != day)
                break;
              else {
                fillSums(holder,mGroupingCursor);
                if (day == thisDayOfYear)
                  headerText = getString(R.string.grouping_today);
                else if (day == thisDayOfYear -1)
                  headerText = getString(R.string.grouping_yesterday);
                else
                  headerText = Utils.convDate(c.getString(columnIndexDate),
                      java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL));
              }
              break traverseCursor;
            case MONTH:
              if (mGroupingCursor.getInt(columnIndexGroupSecond) != month)
                break;
              else {
                fillSums(holder,mGroupingCursor);
                headerText = Utils.convDate(c.getString(columnIndexDate),
                      new SimpleDateFormat("MMMM y"));
              }
                break traverseCursor;
            case WEEK:
              if (mGroupingCursor.getInt(columnIndexGroupSecond) != week)
                break;
              else {
                fillSums(holder,mGroupingCursor);
                //Sqlite3's strftime starts with 0; Calendar with 1
                if (week == thisWeekOfYear -1)
                  headerText = getString(R.string.grouping_this_week);
                else if (week == thisWeekOfYear -2)
                  headerText = getString(R.string.grouping_last_week);
                else
                  headerText = (year != thisYear ? (year + ", ") : "") + getString(R.string.grouping_week) + " " + (week+1);
              }
              break traverseCursor;
            }
          }
          mGroupingCursor.moveToNext();
        }
      }
      holder.text.setText(headerText);
      return convertView;
    }
    private void fillSums(HeaderViewHolder holder, Cursor mGroupingCursor) {
      holder.sumExpense.setText("- " + Utils.convAmount(
          mGroupingCursor.getString(columnIndexGroupSumExpense),
          mAccount.currency));
      holder.sumIncome.setText("+ " + Utils.convAmount(
          mGroupingCursor.getString(columnIndexGroupSumIncome),
          mAccount.currency));
      holder.sumTransfer.setText("<-> " + Utils.convAmount(
          mGroupingCursor.getString(columnIndexGroupSumTransfer),
          mAccount.currency));
    }
    @Override
    public long getHeaderId(int position) {
      if (mAccount.grouping.equals(Account.Grouping.NONE))
        return 0;
      Cursor c = getCursor();
      c.moveToPosition(position);
      int year = c.getInt(columnIndexYear);
      int month = c.getInt(columnIndexMonth);
      int week = c.getInt(columnIndexWeek);
      int day = c.getInt(columnIndexDay);
      switch(mAccount.grouping) {
      case DAY:
        return (year-1900)*200+day;
      case WEEK:
        return (year-1900)*200+week;
      case MONTH:
        return (year-1900)*200+month;
      case YEAR:
        return (year-1900);
      default:
        return 0;
      }
    }
  }
  public class MyAdapter extends SimpleCursorAdapter {
    String categorySeparator = " : ",
        commentSeparator = " / ";

    public MyAdapter(Context context, int layout, Cursor c, String[] from,
        int[] to, int flags) {
      super(context, layout, c, from, to, flags);
    }
    /* (non-Javadoc)
     * calls {@link #convText for formatting the values retrieved from the cursor}
     * @see android.widget.SimpleCursorAdapter#setViewText(android.widget.TextView, java.lang.String)
     */
    @Override
    public void setViewText(TextView v, String text) {
      switch (v.getId()) {
      case R.id.date:
        text = Utils.convDate(text,itemDateFormat);
        break;
      case R.id.amount:
        text = Utils.convAmount(text,mAccount.currency);
      }
      super.setViewText(v, text);
    }
    /* (non-Javadoc)
     * manipulates the view for amount (setting expenses to red) and
     * category (indicate transfer direction with => or <=
     * @see android.widget.CursorAdapter#getView(int, android.view.View, android.view.ViewGroup)
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      convertView=super.getView(position, convertView, parent);

      TextView tv1 = (TextView)convertView.findViewById(R.id.amount);
      Cursor c = getCursor();
      c.moveToPosition(position);
      long amount = c.getLong(columnIndexAmount);
      if (amount < 0) {
        tv1.setTextColor(colorExpense);
        // Set the background color of the text.
      }
      else {
        tv1.setTextColor(colorIncome);
      }
      TextView tv2 = (TextView)convertView.findViewById(R.id.category);
      CharSequence catText = tv2.getText();
      if (DbUtils.getLongOrNull(c,KEY_TRANSFER_PEER) != null) {
        catText = ((amount < 0) ? "=> " : "<= ") + catText;
      } else {
        Long catId = DbUtils.getLongOrNull(c,KEY_CATID);
        if (SPLIT_CATID.equals(catId))
          catText = getString(R.string.split_transaction);
        else if (catId == null) {
          catText = getString(R.string.no_category_assigned);
        }
        else {
          String label_sub = c.getString(columnIndexLabelSub);
          if (label_sub != null && label_sub.length() > 0) {
            catText = catText + categorySeparator + label_sub;
          }
        }
      }
      SpannableStringBuilder ssb;
      String comment = c.getString(columnIndexComment);
      if (comment != null && comment.length() > 0) {
        ssb = new SpannableStringBuilder(comment);
        ssb.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), 0, comment.length(), 0);
        catText = TextUtils.concat(catText,commentSeparator,ssb);
      }
      String payee = c.getString(columnIndexPayee);
      if (payee != null && payee.length() > 0) {
        ssb = new SpannableStringBuilder(payee);
        ssb.setSpan(new UnderlineSpan(), 0, payee.length(), 0);
        catText = TextUtils.concat(catText,commentSeparator,ssb);
      }
      tv2.setText(catText);
      return convertView;
    }
  }
  class HeaderViewHolder {
    TextView text;
    TextView sumIncome;
    TextView sumExpense;
    TextView sumTransfer;
  }
//  @Override
//  public void onHeaderClick(StickyListHeadersListView l, View header,
//      int itemPosition, long headerId, boolean currentlySticky) {
//    View sumLine = header.findViewById(R.id.sum_line);
//    sumLine.setVisibility(sumLine.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
//  }
}
