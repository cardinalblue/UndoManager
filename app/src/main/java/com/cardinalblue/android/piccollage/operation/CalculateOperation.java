package com.cardinalblue.android.piccollage.operation;

import android.os.Parcel;

import com.cardinalblue.android.piccollage.BusProvider;
import com.cardinalblue.android.piccollage.NumberUpdateEvent;
import com.cardinalblue.android.piccollage.UndoManager;

/**
 * Created by prada on 9/21/15.
 */
public class CalculateOperation extends UndoManager.UndoOperation {

    private final int nextNum;
    private final int prevNum;

    public CalculateOperation(int prevNum, int nextNum) {
        this.prevNum = prevNum;
        this.nextNum = nextNum;
    }

    @Override
    public void commit() {
        // do nothing
    }

    @Override
    public void undo() {
        BusProvider.getInstance().post(new NumberUpdateEvent(prevNum));
    }

    @Override
    public void redo() {
        BusProvider.getInstance().post(new NumberUpdateEvent(nextNum));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.nextNum);
        dest.writeInt(this.prevNum);
    }

    protected CalculateOperation(Parcel in) {
        this.nextNum = in.readInt();
        this.prevNum = in.readInt();
    }

    public static final Creator<CalculateOperation> CREATOR = new Creator<CalculateOperation>() {
        public CalculateOperation createFromParcel(Parcel source) {
            return new CalculateOperation(source);
        }

        public CalculateOperation[] newArray(int size) {
            return new CalculateOperation[size];
        }
    };
}
