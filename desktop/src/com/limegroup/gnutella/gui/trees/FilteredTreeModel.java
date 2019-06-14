/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.trees;

import org.limewire.collection.CharSequenceKeyAnalyzer;
import org.limewire.collection.PatriciaTrie;
import org.limewire.util.I18NConvert;
import org.limewire.util.StringUtils;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

/**
 * This class provides a filtered view on an underlying {@link TreeModel}.
 * Nodes may be associated with keywords that can be searched for hiding all
 * nodes that do not match the search term.
 */
public class FilteredTreeModel implements TreeModel {
    /**
     * If set to true, keywords will be converted to lower case before stored in the <code>searchTrie</code>.
     */
    private final boolean ignoreCase;
    private final FilteredTreeModelListener listener;
    private final List<TreeModelListener> listeners = new ArrayList<>();
    /**
     * The underlying data model.
     */
    private TreeModel model;
    private ParentProvider parentProvider;
    /**
     * Maps search keywords to lists of matching nodes.
     */
    private final PatriciaTrie<String, List<Object>> searchTrie = new PatriciaTrie<>(new CharSequenceKeyAnalyzer());
    /**
     * Currently visible nodes. If <code>null</code>, all nodes are visible.
     */
    private Set<Object> visibleNodes;

    /**
     * Constructs a filtering tree model.
     *
     * @param model      the underlying data model
     * @param ignoreCase if true, filtering is case insensitive
     */
    public FilteredTreeModel(DefaultTreeModel model, boolean ignoreCase) {
        this(model, ignoreCase, new TreeNodeParentProvider());
    }

    /**
     * Constructs a filtering tree model.
     *
     * @param model          the underlying data model
     * @param ignoreCase     if true, filtering is case insensitive
     * @param parentProvider used to retrieve parents of nodes
     */
    private FilteredTreeModel(TreeModel model, boolean ignoreCase, ParentProvider parentProvider) {
        this.ignoreCase = ignoreCase;
        this.listener = new FilteredTreeModelListener();
        setModel(model, parentProvider);
    }

    /**
     * Associates <code>node</code> with a search <code>key</code>.
     */
    public void addSearchKey(Object node, String key) {
        key = normalize(key);
        List<Object> value = searchTrie.computeIfAbsent(key, k -> new ArrayList<>(1));
        value.add(node);
    }

    public void addTreeModelListener(TreeModelListener l) {
        listeners.add(l);
    }

    /**
     * Hides nodes from the tree that do not match <code>text</code>.
     *
     * @param text search text
     */
    public void filterByText(String text) {
        text = normalize(text);
        if (text == null || text.length() == 0) {
            visibleNodes = null;
        } else {
            visibleNodes = new HashSet<>();
            String[] keywords = StringUtils.split(I18NConvert.instance().getNorm(text), " ");
            for (int i = 0; i < keywords.length; i++) {
                SortedMap<String, List<Object>> nodeListByKey = searchTrie.getPrefixedBy(keywords[i]);
                if (i == 0) {
                    for (List<Object> nodes : nodeListByKey.values()) {
                        visibleNodes.addAll(nodes);
                    }
                } else {
                    Set<Object> allNew = new HashSet<>();
                    for (List<Object> nodes : nodeListByKey.values()) {
                        allNew.addAll(nodes);
                    }
                    visibleNodes.retainAll(allNew);
                }
            }
            ensureParentsVisible();
        }
        TreeModelEvent event = new TreeModelEvent(this, new Object[]{model.getRoot()});
        for (TreeModelListener listener : listeners) {
            listener.treeStructureChanged(event);
        }
    }

    public Object getChild(Object parent, int index) {
        if (visibleNodes == null) {
            return model.getChild(parent, index);
        }
        int visibleIndex = 0;
        for (int i = 0, count = model.getChildCount(parent); i < count; i++) {
            Object node = model.getChild(parent, i);
            if (visibleNodes.contains(node) && index == visibleIndex++) {
                return node;
            }
        }
        throw new ArrayIndexOutOfBoundsException();
    }

    public int getChildCount(Object parent) {
        if (visibleNodes == null) {
            return model.getChildCount(parent);
        }
        int visibleCount = 0;
        for (int i = 0, count = model.getChildCount(parent); i < count; i++) {
            if (visibleNodes.contains(model.getChild(parent, i))) {
                visibleCount++;
            }
        }
        return visibleCount;
    }

    public int getIndexOfChild(Object parent, Object child) {
        if (visibleNodes == null) {
            return model.getIndexOfChild(parent, child);
        }
        int visibleIndex = 0;
        for (int i = 0, count = model.getChildCount(parent); i < count; i++) {
            Object node = model.getChild(parent, i);
            if (visibleNodes.contains(node)) {
                if (node == child) {
                    return visibleIndex;
                }
                visibleIndex++;
            }
        }
        return -1;
    }

    public Object getRoot() {
        return model.getRoot();
    }

    public boolean isLeaf(Object node) {
        return model.isLeaf(node);
    }

    private boolean isVisible(Object node) {
        return visibleNodes == null || visibleNodes.contains(node);
    }

    private String normalize(String text) {
        if (text != null) {
            if (ignoreCase) {
                text = text.toLowerCase();
            }
        }
        return text;
    }

    private void reload() {
        TreeModelEvent event = new TreeModelEvent(this, new Object[]{model.getRoot()});
        for (TreeModelListener listener : listeners) {
            listener.treeStructureChanged(event);
        }
    }

    public void removeTreeModelListener(TreeModelListener l) {
        listeners.remove(l);
    }

    /**
     * Sets the underlying data model.
     *
     * @param model          data model
     * @param parentProvider used to retrieve parents of nodes
     */
    private void setModel(TreeModel model, ParentProvider parentProvider) {
        if (model == null || parentProvider == null) {
            throw new IllegalArgumentException();
        }
        if (this.model != null) {
            this.model.removeTreeModelListener(listener);
        }
        this.model = model;
        this.parentProvider = parentProvider;
        this.model.addTreeModelListener(listener);
        searchTrie.clear();
        reset();
    }

    /**
     * Sets all nodes visible.
     */
    private void reset() {
        this.visibleNodes = null;
        reload();
    }

    /**
     * Sets all parents of the visible nodes visible.
     */
    private void ensureParentsVisible() {
        Set<Object> parentNodes = new HashSet<>();
        for (Object node : visibleNodes) {
            Object parentNode = parentProvider.getParent(node);
            while (parentNode != null) {
                parentNodes.add(parentNode);
                parentNode = parentProvider.getParent(parentNode);
            }
        }
        visibleNodes.addAll(parentNodes);
    }

    public void valueForPathChanged(TreePath path, Object newValue) {
        model.valueForPathChanged(path, newValue);
    }

    /**
     * Interface to retrieve parent nodes.
     */
    interface ParentProvider {
        /**
         * Returns the parent of <code>node</code>.
         *
         * @return null, if <code>node</code> does not have a parent; the parent, otherwise
         */
        Object getParent(Object node);
    }

    /**
     * Implements <code>TreeNodeParentProvider</code> for tree models that use
     * {@link TreeNode} objects such as {@link DefaultTreeModel}.
     */
    public static class TreeNodeParentProvider implements ParentProvider {
        public Object getParent(Object node) {
            return ((TreeNode) node).getParent();
        }
    }

    /**
     * Forwards events from the underlying data model to listeners.
     */
    private class FilteredTreeModelListener implements TreeModelListener {
        TreeModelEvent refactorEvent(TreeModelEvent event) {
            if (visibleNodes != null) {
                List<Object> children = new ArrayList<>(event.getChildren().length);
                List<Integer> indicieList = new ArrayList<>(event.getChildIndices().length);
                visibleNodes.addAll(Arrays.asList(event.getChildren()));
                Object parent = event.getTreePath().getLastPathComponent();
                for (Object node : event.getChildren()) {
                    children.add(node);
                    indicieList.add(getIndexOfChild(parent, node));
                }
                int[] indicies = new int[indicieList.size()];
                for (int i = 0; i < indicies.length; i++) {
                    indicies[i] = indicieList.get(i);
                }
                event = new TreeModelEvent(event.getSource(), event.getTreePath(), indicies, children.toArray(new Object[0]));
            }
            return event;
        }

        public void treeNodesChanged(TreeModelEvent event) {
            if (!isVisible(event.getTreePath().getLastPathComponent())) {
                return;
            }
            event = refactorEvent(event);
            for (TreeModelListener listener : listeners) {
                listener.treeNodesChanged(event);
            }
        }

        public void treeNodesInserted(TreeModelEvent event) {
            if (!isVisible(event.getTreePath().getLastPathComponent())) {
                return;
            }
            event = refactorEvent(event);
            for (TreeModelListener listener : listeners) {
                listener.treeNodesInserted(event);
            }
        }

        public void treeNodesRemoved(TreeModelEvent event) {
            if (!isVisible(event.getTreePath().getLastPathComponent())) {
                return;
            }
            for (TreeModelListener listener : listeners) {
                listener.treeStructureChanged(event);
            }
        }

        public void treeStructureChanged(TreeModelEvent event) {
            if (!isVisible(event.getTreePath().getLastPathComponent())) {
                return;
            }
            for (TreeModelListener listener : listeners) {
                listener.treeStructureChanged(event);
            }
        }
    }
}
