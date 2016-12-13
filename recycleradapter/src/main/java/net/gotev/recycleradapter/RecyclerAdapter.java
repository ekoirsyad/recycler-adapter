package net.gotev.recycleradapter;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.util.Pair;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;

import static android.support.v7.widget.helper.ItemTouchHelper.DOWN;
import static android.support.v7.widget.helper.ItemTouchHelper.END;
import static android.support.v7.widget.helper.ItemTouchHelper.START;
import static android.support.v7.widget.helper.ItemTouchHelper.UP;

/**
 * Helper class to easily work with Android's RecyclerView.Adapter.
 * @author Aleksandar Gotev
 */
public class RecyclerAdapter extends RecyclerView.Adapter<RecyclerAdapterViewHolder>
        implements RecyclerAdapterNotifier{

    private LinkedHashMap<String, Integer> typeIds;
    private LinkedHashMap<Integer, AdapterItem> types;
    private List<AdapterItem> itemsList;
    private AdapterItem emptyItem;
    private int emptyItemId;

    private List<AdapterItem> filtered;
    private boolean showFiltered;

    /**
     * Applies swipe gesture detection on a RecyclerView items.
     * @param recyclerView recycler view o which to apply the swipe gesture
     * @param listener listener called when a swipe is performed on one of the items
     */
    public static void applySwipeGesture(RecyclerView recyclerView, final SwipeListener listener) {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                                  RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {
                listener.onItemSwiped(viewHolder.getAdapterPosition(), swipeDir);
            }
        }).attachToRecyclerView(recyclerView);
    }

    /**
     * Creates a new recyclerAdapter
     */
    public RecyclerAdapter() {
        typeIds = new LinkedHashMap<>();
        types = new LinkedHashMap<>();
        itemsList = new ArrayList<>();
        emptyItem = null;
    }

    private List<AdapterItem> getItems() {
        return showFiltered ? filtered : itemsList;
    }

    /**
     * Sets the item to show when the recycler adapter is empty.
     * @param item item to show when the recycler adapter is empty
     */
    public void setEmptyItem(AdapterItem item) {
        emptyItem = item;
        emptyItemId = ViewIdGenerator.generateViewId();

        if (getItems().isEmpty())
            notifyItemInserted(0);
    }

    /**
     * Adds a new item to this adapter
     * @param item item to add
     * @return {@link RecyclerAdapter}
     */
    public RecyclerAdapter add(AdapterItem item) {
        String className = item.getClass().getName();

        registerItemClass(item, className);
        getItems().add(item);
        removeEmptyItemIfItHasBeenConfigured();

        notifyItemInserted(getItems().size() - 1);
        return this;
    }

    /**
     * Adds a new item to this adapter
     * @param item item to add
     * @param position position at which to add the element. The item previously at
     *                 (position) will be at (position + 1) and the same for all the subsequent
     *                 elements
     * @return {@link RecyclerAdapter}
     */
    public RecyclerAdapter addAtPosition(AdapterItem item, int position) {
        String className = item.getClass().getName();

        registerItemClass(item, className);
        getItems().add(position, item);
        removeEmptyItemIfItHasBeenConfigured();

        notifyItemInserted(position);
        return this;
    }

    private void registerItemClass(AdapterItem item, String className) {
        if (!typeIds.containsKey(className)) {
            int viewId = ViewIdGenerator.generateViewId();
            typeIds.put(className, viewId);
            types.put(viewId, item);
        }
    }

    private void removeEmptyItemIfItHasBeenConfigured() {
        // this is necessary to prevent IndexOutOfBoundsException on RecyclerView when the
        // first item gets added and an empty item has been configured
        if (getItems().size() == 1 && emptyItem != null) {
            notifyItemRemoved(0);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (adapterIsEmptyAndEmptyItemIsDefined()) {
            return emptyItemId;
        }

        AdapterItem item = getItems().get(position);
        String className = item.getClass().getName();
        return typeIds.get(className);
    }

    @Override
    public RecyclerAdapterViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        try {
            AdapterItem item;

            if (adapterIsEmptyAndEmptyItemIsDefined() && viewType == emptyItemId) {
                item = emptyItem;
            } else {
                item = types.get(viewType);
            }

            Context ctx = parent.getContext();
            View view = LayoutInflater.from(ctx).inflate(item.getLayoutId(), parent, false);
            return item.getViewHolder(view, this);

        } catch (NoSuchMethodException exc) {
            Log.e(getClass().getSimpleName(), "onCreateViewHolder error: you should declare " +
                    "a constructor like this in your ViewHolder: " +
                    "public RecyclerAdapterViewHolder(View itemView, RecyclerAdapterNotifier adapter)");
            return null;

        } catch (IllegalAccessException exc) {
            Log.e(getClass().getSimpleName(), "Your ViewHolder class in " +
                    types.get(viewType).getClass().getName() + " should be public!");
            return null;

        } catch (Exception exc) {
            Log.e(getClass().getSimpleName(), "onCreateViewHolder error", exc);
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onBindViewHolder(RecyclerAdapterViewHolder holder, int position) {
        if (adapterIsEmptyAndEmptyItemIsDefined()) {
            emptyItem.bind(holder);
        } else {
            getItems().get(position).bind(holder);
        }
    }

    @Override
    public int getItemCount() {
        if (adapterIsEmptyAndEmptyItemIsDefined())
            return 1;

        return getItems().size();
    }

    @Override
    public void sendEvent(RecyclerAdapterViewHolder holder, Bundle data) {
        int position = holder.getAdapterPosition();

        if (position < 0 || position >= getItems().size())
            return;

        if (getItems().get(position).onEvent(position, data))
            notifyItemChanged(position);
    }

    /**
     * Removes all the items with a certain class from this adapter and automatically notifies changes.
     * @param clazz class of the items to be removed
     */
    public void removeAllItemsWithClass(Class<? extends AdapterItem> clazz) {
        removeAllItemsWithClass(clazz, new RemoveListener() {
            @Override
            public boolean hasToBeRemoved(AdapterItem item) {
                return true;
            }
        });
    }

    /**
     * Removes all the items with a certain class from this adapter and automatically notifies changes.
     * @param clazz class of the items to be removed
     * @param listener listener invoked for every item that is found. If the callback returns true,
     *                 the item will be removed. If it returns false, the item will not be removed
     */
    public void removeAllItemsWithClass(Class<? extends AdapterItem> clazz, RemoveListener listener) {
        if (clazz == null)
            throw new IllegalArgumentException("The class of the items can't be null!");

        if (listener == null)
            throw new IllegalArgumentException("RemoveListener can't be null!");

        if (getItems().isEmpty())
            return;

        ListIterator<AdapterItem> iterator = getItems().listIterator();
        int index;
        while (iterator.hasNext()) {
            index = iterator.nextIndex();
            AdapterItem item = iterator.next();
            if (item.getClass().getName().equals(clazz.getName()) && listener.hasToBeRemoved(item)) {
                iterator.remove();
                notifyItemRemoved(index);
            }
        }

        Integer id = typeIds.get(clazz.getName());
        if (id != null) {
            typeIds.remove(clazz.getName());
            types.remove(id);
        }
    }

    /**
     * Gets the last item with a given class, together with its position.
     * @param clazz class of the item to search
     * @return Pair with position and AdapterItem or null if the adapter is empty or no items
     * exists with the given class
     */
    public Pair<Integer, AdapterItem> getLastItemWithClass(Class<? extends AdapterItem> clazz) {
        if (clazz == null)
            throw new IllegalArgumentException("The class of the items can't be null!");

        if (getItems().isEmpty())
            return null;

        for (int i = getItems().size() - 1; i >= 0; i--) {
            if (getItems().get(i).getClass().getName().equals(clazz.getName())) {
                return new Pair<>(i, getItems().get(i));
            }
        }

        return null;
    }

    /**
     * Removes only the last item with a certain class from the adapter.
     * @param clazz class of the item to remove
     */
    public void removeLastItemWithClass(Class<? extends AdapterItem> clazz) {
        if (getItems().isEmpty())
            return;

        for (int i = getItems().size() - 1; i >= 0; i--) {
            if (getItems().get(i).getClass().getName().equals(clazz.getName())) {
                getItems().remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    /**
     * Removes an item in a certain position. Does nothing if the adapter is empty or if the
     * position specified is out of adapter bounds.
     * @param position position to be removed
     */
    public void removeItemAtPosition(int position) {
        if (getItems().isEmpty() || position < 0 || position >= getItems().size())
            return;

        getItems().remove(position);
        notifyItemRemoved(position);
    }

    /**
     * Gets an item at a given position.
     * @param position item position
     * @return {@link AdapterItem} or null if the adapter is empty or the position is out of bounds
     */
    public AdapterItem getItemAtPosition(int position) {
        if (getItems().isEmpty() || position < 0 || position >= getItems().size())
            return null;

        return getItems().get(position);
    }

    /**
     * Clears all the elements in the adapter.
     */
    public void clear() {
        int itemsSize = getItems().size();
        getItems().clear();
        if (itemsSize > 0) {
            notifyItemRangeRemoved(0, itemsSize);
        }
    }

    private boolean adapterIsEmptyAndEmptyItemIsDefined() {
        return getItems().isEmpty() && emptyItem != null;
    }

    /**
     * Enables reordering of the list through drap and drop.
     * @param recyclerView recycler view on which to apply the drag and drop
     */
    public void enableDragDrop(RecyclerView recyclerView) {
        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                return makeFlag(ItemTouchHelper.ACTION_STATE_DRAG, DOWN | UP | START | END);
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                int sourcePosition = viewHolder.getAdapterPosition();
                int targetPosition = target.getAdapterPosition();

                Collections.swap(getItems(), sourcePosition, targetPosition);
                notifyItemMoved(sourcePosition, targetPosition);

                return true;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                //Do nothing here
            }
        });

        touchHelper.attachToRecyclerView(recyclerView);
    }

    /**
     * Filters this adapter with a given search term and shows only the items which
     * matches it.
     * @param searchTerm search term
     */
    public void filter(final String searchTerm) {
        if (itemsList == null || itemsList.isEmpty()) {
            return;
        }

        if (searchTerm == null || searchTerm.isEmpty()) {
            showFiltered = false;
            notifyDataSetChanged();
            return;
        }

        if (filtered == null) {
            filtered = new ArrayList<>();
        } else {
            filtered.clear();
        }

        for (AdapterItem item : itemsList) {
            if (item.onFilter(searchTerm)) {
                filtered.add(item);
            }
        }

        showFiltered = true;
        notifyDataSetChanged();

    }
}
