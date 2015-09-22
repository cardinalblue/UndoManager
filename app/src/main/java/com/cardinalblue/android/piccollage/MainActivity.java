package com.cardinalblue.android.piccollage;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.cardinalblue.android.R;
import com.cardinalblue.android.piccollage.operation.DivideOperation;
import com.cardinalblue.android.piccollage.operation.MinusOperation;
import com.cardinalblue.android.piccollage.operation.MultiplyOperation;
import com.cardinalblue.android.piccollage.operation.PlusOperation;
import com.squareup.otto.Subscribe;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String KEY_UNDO_STACK  = "key_undo_stack";
    private static final String KEY_LAST_NUM    = "key_last_num";
    private UndoManager mUndoMgr;
    private TextView mResultText;
    private MenuItem mRedoItem;
    private MenuItem mUndoItem;
    private EditText mEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        findViewById(R.id.btn_divide).setOnClickListener(this);
        findViewById(R.id.btn_minus).setOnClickListener(this);
        findViewById(R.id.btn_multiple).setOnClickListener(this);
        findViewById(R.id.btn_plus).setOnClickListener(this);
        mEditText = (EditText) findViewById(R.id.edit_field);
        mResultText = (TextView) findViewById(R.id.text_result);
        mUndoMgr = new UndoManager();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        mUndoItem = menu.findItem(R.id.menuitem_undo);
        mRedoItem = menu.findItem(R.id.menuitem_redo);
        updateMenuItem();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menuitem_redo:
                if (mUndoMgr.canRedo()) {
                    mUndoMgr.redo(1);
                    updateMenuItem();
                }
                return true;
            case R.id.menuitem_undo:
                if (mUndoMgr.canUndo()) {
                    mUndoMgr.undo(1);
                    updateMenuItem();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_UNDO_STACK, mUndoMgr.saveInstanceState());
        outState.putString(KEY_LAST_NUM, String.valueOf(mResultText.getText()));

    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mUndoMgr.restoreInstanceState(savedInstanceState.getParcelable(KEY_UNDO_STACK));
        mResultText.setText(savedInstanceState.getString(KEY_LAST_NUM));
    }

    @Override
    public void onClick(View v) {
        int num;
        try {
            num = Integer.valueOf(String.valueOf(mEditText.getText()));
        } catch (Throwable t) {
            return;
        }
        int prevNum = Integer.valueOf(String.valueOf(mResultText.getText()));
        int nextNum = prevNum;
        switch(v.getId()) {
            case R.id.btn_divide:
                if (num == 0) {
                    return;
                }
                nextNum /= num;
                mUndoMgr.beginUpdate("/" + num);
                mUndoMgr.addOperation(new DivideOperation(nextNum, num));
                mUndoMgr.endUpdate();
                break;
            case R.id.btn_minus:
                if (num == 0) {
                    return;
                }

                nextNum -= num;
                mUndoMgr.beginUpdate("-" + num);
                mUndoMgr.addOperation(new MinusOperation(nextNum, num));
                mUndoMgr.endUpdate();
                break;
            case R.id.btn_multiple:
                if (num == 1) {
                    return;
                }
                nextNum *= num;
                mUndoMgr.beginUpdate("*" + num);
                mUndoMgr.addOperation(new MultiplyOperation(nextNum, num));
                mUndoMgr.endUpdate();
                break;
            case R.id.btn_plus:
                if (num == 0) {
                    return;
                }
                nextNum += num;
                mUndoMgr.beginUpdate("+" + num);
                mUndoMgr.addOperation(new PlusOperation(nextNum, num));
                mUndoMgr.endUpdate();
                break;
            default:
                return;
        }
        mResultText.setText(String.valueOf(nextNum));
        updateMenuItem();
    }
    private void updateMenuItem() {
        mUndoItem.setTitle(mUndoMgr.getUndoLabel() == null ? "Undo" : "Undo\n" + mUndoMgr.getUndoLabel());
        mUndoItem.setEnabled(mUndoMgr.canUndo());
        mRedoItem.setTitle(mUndoMgr.getRedoLabel() == null ? "Redo" : "Redo\n" + mUndoMgr.getRedoLabel());
        mRedoItem.setEnabled(mUndoMgr.canRedo());
    }

    @Override
    protected void onPause() {
        super.onPause();
        BusProvider.getInstance().unregister(this);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        BusProvider.getInstance().register(this);
    }

    @Subscribe
    public void OnNumberChanged(NumberUpdateEvent e) {
        mResultText.setText(String.valueOf(e.number));
    }
}
