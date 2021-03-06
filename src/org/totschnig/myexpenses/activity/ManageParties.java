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

package org.totschnig.myexpenses.activity;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.dialog.ProgressDialogFragment;
import org.totschnig.myexpenses.fragment.TaskExecutionFragment;
import org.totschnig.myexpenses.model.Payee;

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.view.ContextMenu;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.Button;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class ManageParties extends ProtectedFragmentActivity {
  Cursor mPartiesCursor;
  Button mDeleteButton;

  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    setTheme(MyApplication.getThemeId());
    super.onCreate(savedInstanceState);
    setContentView(R.layout.manage_parties);
    setTitle(R.string.pref_manage_parties_title);
  }
  @Override
  public void onCreateContextMenu(ContextMenu menu, View v,
      ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);
    menu.add(0, R.id.DELETE_COMMAND, 0, R.string.menu_delete);
  }

  @Override
  public boolean onContextItemSelected(android.view.MenuItem item) {
    AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
    switch(item.getItemId()) {
    case R.id.DELETE_COMMAND:
      FragmentManager fm = getSupportFragmentManager();
      fm.beginTransaction()
        .add(TaskExecutionFragment.newInstance(TaskExecutionFragment.TASK_DELETE_PAYEE,info.id), "DELETE_TASK")
        .add(ProgressDialogFragment.newInstance(R.string.progress_dialog_deleting),"PROGRESS")
        .commit();
    }
    return super.onContextItemSelected(item);
  }

}
