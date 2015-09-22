package com.cardinalblue.android.piccollage;


/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import java.util.ArrayList;

/**
 * Top-level class for managing and interacting with the global undo state for
 * a document or application.  This class supports both undo and redo and has
 * helpers for merging undoable operations together as they are performed.
 *
 * <p>A single undoable operation is represented by {@link UndoOperation} which
 * apps implement to define their undo/redo behavior.  The UndoManager keeps
 * a stack of undo states; each state can have one or more undo operations
 * inside of it.</p>
 *
 * <p>Updates to the stack must be done inside of a {@link #beginUpdate}/{@link #endUpdate()}
 * pair.  During this time you can add new operations to the stack with
 * {@link #addOperation}, retrieve and modify existing operations with
 * {@link #getLastOperation}, control the label shown to the user for this operation
 * with {@link #setUndoLabel} and {@link #suggestUndoLabel}, etc.</p>
 *
 * <p>For example, you may have a document with multiple embedded objects.  If the
 * document itself and each embedded object use different owners, then you
 * can provide undo semantics appropriate to the user's context: while within
 * an embedded object, only edits to that object are seen and the user can
 * undo/redo them without needing to impact edits in other objects; while
 * within the larger document, all edits can be seen and the user must
 * undo/redo them as a single stream.</p>
 *
 * @hide
 */
public class UndoManager {
    private final ArrayList<UndoState> mUndos = new ArrayList<>();
    private final ArrayList<UndoState> mRedos = new ArrayList<>();
    private int mUpdateCount;
    private int mHistorySize = 20;
    private UndoState mWorking;
    private int mCommitId = 1;
    private boolean mInUndo;
    private boolean mMerged;
    private int mStateSeq;

    /**
     * Never merge with the last undo state.
     */
    public static final int MERGE_MODE_NONE = 0;
    /**
     * Allow merge with the last undo state only if it contains
     * operations with the caller's owner.
     */
    public static final int MERGE_MODE_UNIQUE = 1;
    /**
     * Always allow merge with the last undo state, if possible.
     */
    public static final int MERGE_MODE_ANY = 2;
    /**
     * Flatten the current undo state into a Parcelable object, which can later be restored
     * with {@link #restoreInstanceState(android.os.Parcelable)}.
     */
    public Parcelable saveInstanceState() {
        if (mUpdateCount > 0) {
            throw new IllegalStateException("Can't save state while updating");
        }
        ParcelableParcel pp = new ParcelableParcel(getClass().getClassLoader());
        Parcel p = pp.getParcel();
        mStateSeq++;
        if (mStateSeq <= 0) {
            mStateSeq = 0;
        }
        p.writeInt(mHistorySize);
        // XXX eventually we need to be smart here about limiting the
        // number of undo states we write to not exceed X bytes.
        int i = mUndos.size();
        while (i > 0) {
            p.writeInt(1);
            i--;
            mUndos.get(i).writeToParcel(p);
        }
        i = mRedos.size();
        p.writeInt(i);
        while (i > 0) {
            p.writeInt(2);
            i--;
            mRedos.get(i).writeToParcel(p);
        }
        p.writeInt(0);
        return pp;
    }
    /**
     * Restore an undo state previously created with {@link #saveInstanceState()}.  This will
     * restore the UndoManager's state to almost exactly what it was at the point it had
     * been previously saved; the only information not restored is the data object
     * associated with each {@link UndoOperation}
     */
    public void restoreInstanceState(Parcelable state) {
        if (mUpdateCount > 0) {
            throw new IllegalStateException("Can't save state while updating");
        }
        forgetUndos(-1);
        forgetRedos(-1);
        ParcelableParcel pp = (ParcelableParcel)state;
        Parcel p = pp.getParcel();
        mHistorySize = p.readInt();
        int stype;
        while ((stype=p.readInt()) != 0) {
            UndoState ustate = new UndoState(p, pp.getClassLoader());
            if (stype == 1) {
                mUndos.add(0, ustate);
            } else {
                mRedos.add(0, ustate);
            }
        }
    }
    /**
     * Set the maximum number of undo states that will be retained.
     */
    public void setHistorySize(int size) {
        mHistorySize = size;
        if (mHistorySize >= 0 && countUndos() > mHistorySize) {
            forgetUndos(countUndos() - mHistorySize);
        }
    }
    /**
     * Return the current maximum number of undo states.
     */
    public int getHistorySize() {
        return mHistorySize;
    }

    /**
     * Perform undo of last/top <var>count</var> undo states.  The states impacted
     * by this can be limited through <var>owners</var>.
     * @param count Number of undo states to pop.
     * @return Returns the number of undo states that were actually popped.
     */
    public int undo(int count) {
        if (mWorking != null) {
            throw new IllegalStateException("Can't be called during an update");
        }
        int num = 0;
        int i = -1;
        mInUndo = true;
        UndoState us = getTopUndo();
        if (us != null) {
            us.makeExecuted();
        }
        while (count > 0 && (i=findPrevState(mUndos, i)) >= 0) {
            UndoState state = mUndos.remove(i);
            state.undo();
            mRedos.add(state);
            count--;
            num++;
        }
        mInUndo = false;
        return num;
    }

    /**
     * Perform redo of last/top <var>count</var> undo states in the transient redo stack.
     * The states impacted by this can be limited through <var>owners</var>.
     * @param count Number of undo states to pop.
     * @return Returns the number of undo states that were actually redone.
     */
    public int redo(int count) {
        if (mWorking != null) {
            throw new IllegalStateException("Can't be called during an update");
        }
        int num = 0;
        int i = -1;
        mInUndo = true;
        while (count > 0 && (i=findPrevState(mRedos, i)) >= 0) {
            UndoState state = mRedos.remove(i);
            state.redo();
            mUndos.add(state);
            count--;
            num++;
        }
        mInUndo = false;
        return num;
    }
    /**
     * Returns true if we are currently inside of an undo/redo operation.  This is
     * useful for editors to know whether they should be generating new undo state
     * when they see edit operations happening.
     */
    public boolean isInUndo() {
        return mInUndo;
    }
    public int forgetUndos(int count) {
        if (count < 0) {
            count = mUndos.size();
        }
        int removed = 0;
        for (int i=0; i<mUndos.size() && removed < count; i++) {
            UndoState state = mUndos.get(i);
            if (count > 0) {
                state.destroy();
                mUndos.remove(i);
                removed++;
            }
        }
        return removed;
    }
    public int forgetRedos(int count) {
        if (count < 0) {
            count = mRedos.size();
        }
        int removed = 0;
        for (int i=0; i<mRedos.size() && removed < count; i++) {
            UndoState state = mRedos.get(i);
            if (count > 0) {
                state.destroy();
                mRedos.remove(i);
                removed++;
            }
        }
        return removed;
    }
    /**
     * Return the number of undo states on the undo stack.
     */
    public int countUndos() {
        return mUndos.size();
    }
    /**
     * Return the number of redo states on the undo stack.
     */
    public int countRedos() {
        return mRedos.size();
    }
    /**
     * Return the user-visible label for the top undo state on the stack.
     */
    public CharSequence getUndoLabel() {
        UndoState state = getTopUndo();
        return state != null ? state.getLabel() : null;
    }
    /**
     * Return the user-visible label for the top redo state on the stack.
     */
    public CharSequence getRedoLabel() {
        UndoState state = getTopRedo();
        return state != null ? state.getLabel() : null;
    }
    /**
     * Start creating a new undo state.  Multiple calls to this function will nest until
     * they are all matched by a later call to {@link #endUpdate}.
     * @param label Optional user-visible label for this new undo state.
     */
    public void beginUpdate(CharSequence label) {
        if (mInUndo) {
            throw new IllegalStateException("Can't being update while performing undo/redo");
        }
        if (mUpdateCount <= 0) {
            createWorkingState();
            mMerged = false;
            mUpdateCount = 0;
        }
        mWorking.updateLabel(label);
        mUpdateCount++;
    }
    private void createWorkingState() {
        mWorking = new UndoState(mCommitId++);
        if (mCommitId < 0) {
            mCommitId = 1;
        }
    }
    /**
     * Returns true if currently inside of a {@link #beginUpdate}.
     */
    public boolean isInUpdate() {
        return mUpdateCount > 0;
    }
    /**
     * Forcibly set a new for the new undo state being built within a {@link #beginUpdate}.
     * Any existing label will be replaced with this one.
     */
    public void setUndoLabel(CharSequence label) {
        if (mWorking == null) {
            throw new IllegalStateException("Must be called during an update");
        }
        mWorking.setLabel(label);
    }
    /**
     * Set a new for the new undo state being built within a {@link #beginUpdate}, but
     * only if there is not a label currently set for it.
     */
    public void suggestUndoLabel(CharSequence label) {
        if (mWorking == null) {
            throw new IllegalStateException("Must be called during an update");
        }
        mWorking.updateLabel(label);
    }
    /**
     * Return the number of times {@link #beginUpdate} has been called without a matching
     * {@link #endUpdate} call.
     */
    public int getUpdateNestingLevel() {
        return mUpdateCount;
    }
    /**
     * Check whether there is an {@link UndoOperation} in the current {@link #beginUpdate}
     * undo state.
     * @return Returns true if there is a matching operation in the current undo state.
     */
    public boolean hasOperation() {
        if (mWorking == null) {
            throw new IllegalStateException("Must be called during an update");
        }
        return mWorking.hasOperation();
    }
    /**
     * Return the most recent {@link UndoOperation} that was added to the update.
     * @param mergeMode May be either {@link #MERGE_MODE_NONE} or {@link #MERGE_MODE_ANY}.
     */
    public UndoOperation<?> getLastOperation(int mergeMode) {
        return getLastOperation(null, mergeMode);
    }
    /**
     * Return the most recent {@link UndoOperation} that was added to the update and
     * has the given owner.
     * @param clazz Optional class of the last operation to retrieve.  If null, the
     * last operation regardless of class will be retrieved; if non-null, the last
     * operation whose class is the same as the given class will be retrieved.
     * @param mergeMode May be either {@link #MERGE_MODE_NONE}, {@link #MERGE_MODE_UNIQUE},
     * or {@link #MERGE_MODE_ANY}.
     */
    public <T extends UndoOperation> T getLastOperation(Class<T> clazz, int mergeMode) {
        if (mWorking == null) {
            throw new IllegalStateException("Must be called during an update");
        }
        if (mergeMode != MERGE_MODE_NONE && !mMerged && !mWorking.hasData()) {
            UndoState state = getTopUndo();
            UndoOperation<?> last;
            if (state != null && (mergeMode == MERGE_MODE_ANY)
                    && state.canMerge() && (last=state.getLastOperation(clazz)) != null) {
                if (last.allowMerge()) {
                    mWorking.destroy();
                    mWorking = state;
                    mUndos.remove(state);
                    mMerged = true;
                    return (T)last;
                }
            }
        }
        return mWorking.getLastOperation(clazz);
    }
    public void addOperation(UndoOperation<?> op) {
        addOperation(op, MERGE_MODE_NONE);
    }
    /**
     * Add a new UndoOperation to the current update.
     * @param op The new operation to add.
     * @param mergeMode May be either {@link #MERGE_MODE_NONE}, {@link #MERGE_MODE_UNIQUE},
     * or {@link #MERGE_MODE_ANY}.
     */
    private void addOperation(UndoOperation<?> op, int mergeMode) {
        if (mWorking == null) {
            throw new IllegalStateException("Must be called during an update");
        }
        if (mergeMode != MERGE_MODE_NONE && !mMerged && !mWorking.hasData()) {
            UndoState state = getTopUndo();
            if (state != null && (mergeMode == MERGE_MODE_ANY)
                    && state.canMerge() && state.hasOperation()) {
                mWorking.destroy();
                mWorking = state;
                mUndos.remove(state);
                mMerged = true;
            }
        }
        mWorking.addOperation(op);
    }
    /**
     * Finish the creation of an undo state, matching a previous call to
     * {@link #beginUpdate}.
     */
    public void endUpdate() {
        if (mWorking == null) {
            throw new IllegalStateException("Must be called during an update");
        }
        mUpdateCount--;
        if (mUpdateCount == 0) {
            pushWorkingState();
        }
    }
    private void pushWorkingState() {
        int N = mUndos.size() + 1;
        if (mWorking.hasData()) {
            mUndos.add(mWorking);
            forgetRedos(-1);
            mWorking.commit();
            if (N >= 2) {
                // The state before this one can no longer be merged, ever.
                // The only way to get back to it is for the user to perform
                // an undo.
                mUndos.get(N-2).makeExecuted();
            }
        } else {
            mWorking.destroy();
        }
        mWorking = null;
        if (mHistorySize >= 0 && N > mHistorySize) {
            forgetUndos(N - mHistorySize);
        }
    }
    /**
     * Commit the last finished undo state.  This undo state can no longer be
     * modified with further {@link #MERGE_MODE_UNIQUE} or
     * {@link #MERGE_MODE_ANY} merge modes.  If called while inside of an update,
     * this will push any changes in the current update on to the undo stack
     * and result with a fresh undo state, behaving as if {@link #endUpdate()}
     * had been called enough to unwind the current update, then the last state
     * committed, and {@link #beginUpdate} called to restore the update nesting.
     * @return Returns an integer identifier for the committed undo state, which
     * can later be used to try to uncommit the state to perform further edits on it.
     */
    public int commitState() {
        if (mWorking != null && mWorking.hasData()) {
            if (mWorking.hasOperation()) {
                mWorking.setCanMerge(false);
                int commitId = mWorking.getCommitId();
                pushWorkingState();
                createWorkingState();
                mMerged = true;
                return commitId;
            }
        } else {
            UndoState state = getTopUndo();
            if (state != null) {
                state.setCanMerge(false);
                return state.getCommitId();
            }
        }
        return -1;
    }
    /**
     * Attempt to undo a previous call to {@link #commitState}.  This will work
     * if the undo state at the top of the stack has the given id, and has not been
     * involved in an undo operation.  Otherwise false is returned.
     * @param commitId The identifier for the state to be uncommitted, as returned
     * by {@link #commitState}.
     * @return Returns true if the uncommit is successful, else false.
     */
    public boolean uncommitState(int commitId) {
        if (mWorking != null && mWorking.getCommitId() == commitId) {
            if (mWorking.hasOperation()) {
                return mWorking.setCanMerge(true);
            }
        } else {
            UndoState state = getTopUndo();
            if (state != null) {
                if (state.getCommitId() == commitId) {
                    return state.setCanMerge(true);
                }
            }
        }
        return false;
    }
    public boolean canUndo() {
        return !mUndos.isEmpty();
    }
    public boolean canRedo() {
        return !mRedos.isEmpty();
    }
    UndoState getTopUndo() {
        if (mUndos.size() <= 0) {
            return null;
        }
        int i = findPrevState(mUndos, -1);
        return i >= 0 ? mUndos.get(i) : null;
    }
    UndoState getTopRedo() {
        if (mRedos.size() <= 0) {
            return null;
        }
        int i = findPrevState(mRedos, -1);
        return i >= 0 ? mRedos.get(i) : null;
    }
    int findPrevState(ArrayList<UndoState> states, int from) {
        final int N = states.size();
        if (from == -1) {
            from = N-1;
        }
        if (from >= N) {
            return -1;
        }
        return from;
    }

    final static class UndoState {
        private final int mCommitId;
        private final ArrayList<UndoOperation<?>> mOperations = new ArrayList<UndoOperation<?>>();
        private ArrayList<UndoOperation<?>> mRecent;
        private CharSequence mLabel;
        private boolean mCanMerge = true;
        private boolean mExecuted;
        UndoState(int commitId) {
            mCommitId = commitId;
        }
        UndoState(Parcel p, ClassLoader loader) {
            mCommitId = p.readInt();
            mCanMerge = p.readInt() != 0;
            mExecuted = p.readInt() != 0;
            mLabel = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(p);
            final int N = p.readInt();
            for (int i=0; i<N; i++) {
                UndoOperation op = p.readParcelable(loader);
                mOperations.add(op);
            }
        }
        void writeToParcel(Parcel p) {
            if (mRecent != null) {
                throw new IllegalStateException("Can't save state before committing");
            }
            p.writeInt(mCommitId);
            p.writeInt(mCanMerge ? 1 : 0);
            p.writeInt(mExecuted ? 1 : 0);
            TextUtils.writeToParcel(mLabel, p, 0);
            final int N = mOperations.size();
            p.writeInt(N);
            for (int i=0; i<N; i++) {
                UndoOperation op = mOperations.get(i);
                p.writeParcelable(op, 0);
            }
        }
        int getCommitId() {
            return mCommitId;
        }
        void setLabel(CharSequence label) {
            mLabel = label;
        }
        void updateLabel(CharSequence label) {
//            if (mLabel != null) {
                mLabel = label;
//            }
        }
        CharSequence getLabel() {
            return mLabel;
        }
        boolean setCanMerge(boolean state) {
            // Don't allow re-enabling of merging if state has been executed.
            if (state && mExecuted) {
                return false;
            }
            mCanMerge = state;
            return true;
        }
        void makeExecuted() {
            mExecuted = true;
        }
        boolean canMerge() {
            return mCanMerge && !mExecuted;
        }
        int countOperations() {
            return mOperations.size();
        }
        boolean hasOperation() {
            final int N = mOperations.size();
            return N != 0;
        }
        void addOperation(UndoOperation<?> op) {
            if (mOperations.contains(op)) {
                throw new IllegalStateException("Already holds " + op);
            }
            mOperations.add(op);
            if (mRecent == null) {
                mRecent = new ArrayList<>();
                mRecent.add(op);
            }
        }
        <T extends UndoOperation> T getLastOperation(Class<T> clazz) {
            final int N = mOperations.size();
            if (clazz == null) {
                return N > 0 ? (T)mOperations.get(N-1) : null;
            }
            // First look for the top-most operation with the same owner.
            for (int i=N-1; i>=0; i--) {
                UndoOperation<?> op = mOperations.get(i);
                // Return this operation if it has the same class that the caller wants.
                // Note that we don't search deeper for the class, because we don't want
                // to end up with a different order of operations for the same owner.
                if (clazz != null && op.getClass() != clazz) {
                    return null;
                }
                return (T)op;
            }
            return null;
        }
        boolean hasData() {
            for (int i=mOperations.size()-1; i>=0; i--) {
                if (mOperations.get(i).hasData()) {
                    return true;
                }
            }
            return false;
        }
        void commit() {
            final int N = mRecent != null ? mRecent.size() : 0;
            for (int i=0; i<N; i++) {
                mRecent.get(i).commit();
            }
            mRecent = null;
        }
        void undo() {
            for (int i=mOperations.size()-1; i>=0; i--) {
                mOperations.get(i).undo();
            }
        }
        void redo() {
            final int N = mOperations.size();
            for (int i=0; i<N; i++) {
                mOperations.get(i).redo();
            }
        }
        void destroy() {

        }
    }

    /**
     * A single undoable operation.  You must subclass this to implement the state
     * and behavior for your operation.  Instances of this class are placed and
     * managed in an {@link UndoManager}.
     *
     * @hide
     */
    public static abstract class UndoOperation<DATA> implements Parcelable {
        protected UndoOperation() {
        }
        /**
         * Construct from a Parcel.
         */
        protected UndoOperation(Parcel src, ClassLoader loader) {
        }
        /**
         * Return true if this operation actually contains modification data.  The
         * default implementation always returns true.  If you return false, the
         * operation will be dropped when the final undo state is being built.
         */
        public boolean hasData() {
            return true;
        }
        /**
         * Return true if this operation can be merged with a later operation.
         * The default implementation always returns true.
         */
        public boolean allowMerge() {
            return true;
        }
        /**
         * Called when this undo state is being committed to the undo stack.
         * The implementation should perform the initial edits and save any state that
         * may be needed to undo them.
         */
        public abstract void commit();
        /**
         * Called when this undo state is being popped off the undo stack (in to
         * the temporary redo stack).  The implementation should remove the original
         * edits and thus restore the target object to its prior value.
         */
        public abstract void undo();
        /**
         * Called when this undo state is being pushed back from the transient
         * redo stack to the main undo stack.  The implementation should re-apply
         * the edits that were previously removed by {@link #undo}.
         */
        public abstract void redo();
        public int describeContents() {
            return 0;
        }
    }
}