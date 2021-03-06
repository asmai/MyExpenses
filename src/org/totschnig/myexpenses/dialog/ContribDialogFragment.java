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

package org.totschnig.myexpenses.dialog;

import java.io.Serializable;

import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.activity.ContribIFace;
import org.totschnig.myexpenses.dialog.MessageDialogFragment.MessageDialogListener;
import org.totschnig.myexpenses.model.ContribFeature.Feature;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.Html;
import android.text.TextUtils;

public class ContribDialogFragment extends DialogFragment implements DialogInterface.OnClickListener{
  Feature feature;
  int usagesLeft;

  public static final ContribDialogFragment newInstance(Feature feature, Serializable tag) {
    ContribDialogFragment dialogFragment = new ContribDialogFragment();
    Bundle bundle = new Bundle();
    bundle.putSerializable("feature", feature);
    bundle.putSerializable("tag", tag);
    dialogFragment.setArguments(bundle);
    return dialogFragment;
  }
  @Override
  public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      final Bundle bundle = getArguments();
      feature = (Feature) bundle.getSerializable("feature");
      usagesLeft = feature.usagesLeft();
  }
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    Activity ctx  = getActivity();
    Context wrappedCtx = DialogUtils.wrapContext2(ctx);
    CharSequence message = TextUtils.concat(
        Html.fromHtml(String.format(getString(
          R.string.dialog_contrib_reminder,
          getString(getResources().getIdentifier(
              "contrib_feature_" + feature + "_label", "string", getActivity().getPackageName())),
          usagesLeft > 0 ? getString(R.string.dialog_contrib_usage_count,usagesLeft) :
                getString(R.string.dialog_contrib_no_usages_left)))),
        " ",
        getString(R.string.thank_you));
      return new AlertDialog.Builder(wrappedCtx)
        .setTitle(R.string.dialog_title_contrib_feature)
        .setMessage(message)
      //null should be your on click listener
        .setNegativeButton(R.string.dialog_contrib_no, this)
        .setPositiveButton(R.string.dialog_contrib_yes, this)
        .create();
  }
  @Override
  public void onClick(DialogInterface dialog, int which) {
    Context ctx = getActivity();
    if (which == AlertDialog.BUTTON_POSITIVE) {
      ((MessageDialogListener) ctx).dispatchCommand(R.id.CONTRIB_PLAY_COMMAND,null);
    } else {
      if (usagesLeft > 0) {
        ((ContribIFace)ctx).contribFeatureCalled(feature, getArguments().getSerializable("tag"));
      } else {
        ((ContribIFace)ctx).contribFeatureNotCalled();
      }
    }
  }
  @Override
  public void onCancel (DialogInterface dialog) {
    ((ContribIFace)getActivity()).contribFeatureNotCalled();
  }
}