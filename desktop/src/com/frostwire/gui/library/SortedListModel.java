/*
 * SortedListModel.java
 *
 * Copyright 2006 Sun Microsystems, Inc. ALL RIGHTS RESERVED Use of
 * this software is authorized pursuant to the terms of the license
 * found at http://developers.sun.com/berkeley_license.html .
 *
 */
package com.frostwire.gui.library;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.text.Collator;
import java.util.*;

/**
 * SortedListModel decorates an unsorted ListModel to provide
 * a sorted model. You can create a SortedListModel from models you
 * already have. Place the SortedListModel into a JList, for example, to provide
 * a sorted view of your underlying model.
 *
 * @author John O'Conner
 */
@SuppressWarnings({"serial", "unused", "rawtypes", "unchecked"})
public class SortedListModel extends AbstractListModel<Object> {
    private List<SortedListEntry> sortedModel;
    private ListModel unsortedModel;
    private Comparator comparator;
    private SortOrder sortOrder;

    private SortedListModel() {
    }

    /**
     * Create a SortedListModel from an existing model
     * using a default text comparator for the default Locale. Sort
     * in ascending order.
     *
     * @param model the underlying, unsorted ListModel
     */
    public SortedListModel(ListModel model) {
        this(model, SortOrder.ASCENDING, null);
    }

    /**
     * Create a SortedListModel from an existing model
     * using a specific comparator and sort order. Use
     * a default text comparator.
     *
     * @param model     the unsorted list model
     * @param sortOrder that should be used
     */
    public SortedListModel(ListModel model, SortOrder sortOrder) {
        this(model, sortOrder, null);
    }

    /**
     * Create a SortedListModel from an existing model. Sort the model
     * in the specified sort order using the given comparator.
     *
     * @param model
     * @param sortOrder
     * @param comp
     */
    public SortedListModel(ListModel model, SortOrder sortOrder, Comparator comp) {
        unsortedModel = model;
        unsortedModel.addListDataListener(new ListDataListener() {
            public void intervalAdded(ListDataEvent e) {
                unsortedIntervalAdded(e);
            }

            public void intervalRemoved(ListDataEvent e) {
                unsortedIntervalRemoved(e);
            }

            public void contentsChanged(ListDataEvent e) {
                unsortedContentsChanged(e);
            }
        });
        this.sortOrder = sortOrder;
        if (comp != null) {
            comparator = comp;
        } else {
            comparator = Collator.getInstance();
        }
        // get base model info
        int size = model.getSize();
        sortedModel = new ArrayList<>(size);
        for (int x = 0; x < size; ++x) {
            SortedListEntry entry = new SortedListEntry(x);
            int insertionPoint = findInsertionPoint(entry);
            sortedModel.add(insertionPoint, entry);
        }
    }

    /**
     * Retrieve the sorted entry from the original model
     *
     * @param index index of an entry in the sorted model
     * @return element in the original model to which our entry points
     */
    public Object getElementAt(int index) throws IndexOutOfBoundsException {
        int modelIndex = toUnsortedModelIndex(index);
        return unsortedModel.getElementAt(modelIndex);
    }

    /**
     * Retrieve the size of the underlying model
     *
     * @return size of the model
     */
    public int getSize() {
        return sortedModel.size();
    }

    /**
     * Convert sorted model index to an unsorted model index.
     *
     * @param index an index in the sorted model
     * @return modelIndex an index in the unsorted model
     */
    public int toUnsortedModelIndex(int index) throws IndexOutOfBoundsException {
        int modelIndex = -1;
        SortedListEntry entry = sortedModel.get(index);
        modelIndex = entry.getIndex();
        return modelIndex;
    }

    /**
     * Convert an array of sorted model indices to their unsorted model indices. Sort
     * the resulting set of indices.
     *
     * @param sortedSelectedIndices indices of selected elements in the sorted model
     *                              or sorted view
     * @return unsortedSelectedIndices selected indices in the unsorted model
     */
    public int[] toUnsortedModelIndices(int[] sortedSelectedIndices) {
        int[] unsortedSelectedIndices = new int[sortedSelectedIndices.length];
        int x = 0;
        for (int sortedIndex : sortedSelectedIndices) {
            unsortedSelectedIndices[x++] = toUnsortedModelIndex(sortedIndex);
        }
        // sort the array of indices before returning
        Arrays.sort(unsortedSelectedIndices);
        return unsortedSelectedIndices;
    }

    /**
     * Convert an unsorted model index to a sorted model index.
     *
     * @param unsortedIndex an element index in the unsorted model
     * @return sortedIndex an element index in the sorted model
     */
    public int toSortedModelIndex(int unsortedIndex) {
        int sortedIndex = -1;
        int x = -1;
        for (SortedListEntry entry : sortedModel) {
            ++x;
            if (entry.getIndex() == unsortedIndex) {
                sortedIndex = x;
                break;
            }
        }
        return sortedIndex;
    }

    /**
     * Convert an array of unsorted model selection indices to
     * indices in the sorted model. Sort the model indices from
     * low to high to duplicate JList's getSelectedIndices method
     *
     * @param unsortedModelIndices
     * @return an array of selected indices in the sorted model
     */
    public int[] toSortedModelIndices(int[] unsortedModelIndices) {
        int[] sortedModelIndices = new int[unsortedModelIndices.length];
        int x = 0;
        for (int unsortedIndex : unsortedModelIndices) {
            sortedModelIndices[x++] = toSortedModelIndex(unsortedIndex);
        }
        Arrays.sort(sortedModelIndices);
        return sortedModelIndices;
    }

    private void resetModelData() {
        int index = 0;
        for (SortedListEntry entry : sortedModel) {
            entry.setIndex(index++);
        }
    }

    public void setComparator(Comparator comp) {
        if (comp == null) {
            sortOrder = SortOrder.UNORDERED;
            comparator = Collator.getInstance();
            resetModelData();
        } else {
            comparator = comp;
            Collections.sort(sortedModel);
        }
        fireContentsChanged(ListDataEvent.CONTENTS_CHANGED, 0, sortedModel.size() - 1);
    }

    /**
     * Change the sort order of the model at runtime
     *
     * @param sortOrder
     */
    public void setSortOrder(SortOrder sortOrder) {
        if (this.sortOrder != sortOrder) {
            this.sortOrder = sortOrder;
            if (sortOrder == SortOrder.UNORDERED) {
                resetModelData();
            } else {
                Collections.sort(sortedModel);
            }
            fireContentsChanged(ListDataEvent.CONTENTS_CHANGED, 0, sortedModel.size() - 1);
        }
    }

    /**
     * Update the sorted model whenever new items
     * are added to the original/decorated model.
     */
    private void unsortedIntervalAdded(ListDataEvent e) {
        int begin = e.getIndex0();
        int end = e.getIndex1();
        int nElementsAdded = end - begin + 1;

        /* Items in the decorated model have shifted in flight.
         * Increment our model pointers into the decorated model.
         * We must increment indices that intersect with the insertion
         * point in the decorated model.
         */
        for (SortedListEntry entry : sortedModel) {
            int index = entry.getIndex();
            // if our model points to a model index >= to where
            // new model entries are added, we must bump up their index
            if (index >= begin) {
                entry.setIndex(index + nElementsAdded);
            }
        }
        // now add the new items from the decorated model
        for (int x = begin; x <= end; ++x) {
            SortedListEntry newEntry = new SortedListEntry(x);
            int insertionPoint = findInsertionPoint(newEntry);
            sortedModel.add(insertionPoint, newEntry);
            fireIntervalAdded(ListDataEvent.INTERVAL_ADDED, insertionPoint, insertionPoint);
        }
    }

    /**
     * Update this model when items are removed from the original/decorated
     * model. Also, let our listeners know that we've removed items.
     */
    private void unsortedIntervalRemoved(ListDataEvent e) {
        int begin = e.getIndex0();
        int end = e.getIndex1();
        int nElementsRemoved = end - begin + 1;

        /*
         * Move from end to beginning of our sorted model, updating
         * element indices into the decorated model or removing
         * elements as necessary
         */
        int sortedSize = sortedModel.size();
        boolean[] bElementRemoved = new boolean[sortedSize];
        for (int x = sortedSize - 1; x >= 0; --x) {
            SortedListEntry entry = sortedModel.get(x);
            int index = entry.getIndex();
            if (index > end) {
                entry.setIndex(index - nElementsRemoved);
            } else if (index >= begin) {
                sortedModel.remove(x);
                bElementRemoved[x] = true;
            }
        }
        /*
         * Let listeners know that we've removed items.
         */
        for (int x = bElementRemoved.length - 1; x >= 0; --x) {
            if (bElementRemoved[x]) {
                fireIntervalRemoved(ListDataEvent.INTERVAL_REMOVED, x, x);
            }
        }
    }

    /**
     * Resort the sorted model if there are changes in the original
     * unsorted model. Let any listeners know about changes. Since I don't
     * track specific changes, sort everywhere and redisplay all items.
     */
    private void unsortedContentsChanged(ListDataEvent e) {
        Collections.sort(sortedModel);
        fireContentsChanged(ListDataEvent.CONTENTS_CHANGED, 0, sortedModel.size() - 1);
    }

    /**
     * Internal helper method to find the insertion point for a new
     * entry in the sorted model.
     */
    private int findInsertionPoint(SortedListEntry entry) {
        int insertionPoint = sortedModel.size();
        if (sortOrder != SortOrder.UNORDERED) {
            insertionPoint = Collections.binarySearch((List) sortedModel, entry);
            if (insertionPoint < 0) {
                insertionPoint = -(insertionPoint + 1);
            }
        }
        return insertionPoint;
    }

    public enum SortOrder {
        UNORDERED,
        ASCENDING,
        DESCENDING
    }

    class SortedListEntry implements Comparable {
        private int index;

        private SortedListEntry() {
        }

        public SortedListEntry(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public int compareTo(Object o) {
            // retrieve the element that this entry points to
            // in the original model
            Object thisElement = unsortedModel.getElementAt(index);
            SortedListEntry thatEntry = (SortedListEntry) o;
            // retrieve the element that thatEntry points to in the original
            // model
            Object thatElement = unsortedModel.getElementAt(thatEntry.getIndex());
            if (comparator instanceof Collator) {
                thisElement = thisElement.toString();
                thatElement = thatElement.toString();
            }
            // compare the base model's elements using the provided comparator
            int comparison = comparator.compare(thisElement, thatElement);
            // convert to descending order as necessary
            if (sortOrder == SortOrder.DESCENDING) {
                comparison = -comparison;
            }
            return comparison;
        }
    }
}
