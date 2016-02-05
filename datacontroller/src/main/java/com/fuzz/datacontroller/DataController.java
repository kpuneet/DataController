package com.fuzz.datacontroller;

import com.fuzz.datacontroller.data.IDataStore;
import com.fuzz.datacontroller.data.MemoryDataStore;
import com.fuzz.datacontroller.fetcher.DataFetcher;
import com.fuzz.datacontroller.strategy.IRefreshStrategy;

/**
 * Description: Responsible for managing how data is loaded, stored, and handled outside of Activity,
 * or Android life-cycle events.
 * <p/>
 * Each hook in this class provides an abstracted mechanism by which you must supply that pipes the
 * information back through the {@link IDataControllerCallback}.
 * <p/>
 * {@link DataController} utilize a {@link DataFetcher} to define how we retrieve data, mapping it back to the {@link IDataCallback}
 * within this {@link DataController}.
 * <p/>
 * Data storage is defined by the {@link IDataStore} interface. For most uses you will want to use a {@link MemoryDataStore}
 * to keep it in memory as you need it.
 */
public class DataController<TResponse> {


    /**
     * Simple interface for checking if empty.
     *
     * @param <TResponse>
     */
    public interface IEmptyChecker<TResponse> {

        /**
         * @param response The response to validate.
         * @return True if the response is deemed empty here.
         */
        boolean isEmpty(TResponse response);
    }

    public enum State {
        /**
         * No state. Nothing has been started. Results from app launching or data clearing
         */
        NONE,

        /**
         * Loading state. We are performing some network or async call on a different thread. This
         * prevents us from making the same call twice.
         */
        LOADING,

        /**
         * Empty state. The resulting response is empty.
         */
        EMPTY,

        /**
         * Last update was a success and we have data.
         */
        SUCCESS,

        /**
         * Last update failed and our data may or may not be up to date.
         */
        FAILURE
    }

    private DataFetcher<TResponse> dataFetcher;
    private IDataStore<TResponse> dataStore;
    private IRefreshStrategy refreshStrategy;
    private State state = State.NONE;
    private IEmptyChecker<TResponse> emptyChecker;

    private final DataControllerCallbackGroup<TResponse> dataControllerGroup = new DataControllerCallbackGroup<>();


    /**
     * Public constructor.
     */
    public DataController() {
    }

    DataController(DataFetcher<TResponse> dataFetcher, IDataStore<TResponse> dataStore, IRefreshStrategy refreshStrategy, IEmptyChecker<TResponse> emptyChecker) {
        this.dataFetcher = dataFetcher;
        this.dataStore = dataStore;
        this.refreshStrategy = refreshStrategy;
        this.emptyChecker = emptyChecker;
    }

    /**
     * @return True if {@link #getStoredData()} is considered {@link #isEmpty(TResponse)}.
     */
    public boolean hasStoredData() {
        return !isEmpty(getStoredData());
    }

    /**
     * Register for callbacks on this instance to get notified when {@link State} changes.
     */
    public void registerForCallbacks(IDataControllerCallback<DataControllerResponse<TResponse>> dataControllerCallback) {
        dataControllerGroup.registerForCallbacks(dataControllerCallback);
    }

    /**
     * Deregister for callbacks.
     */
    public void deregisterForCallbacks(IDataControllerCallback<DataControllerResponse<TResponse>> dataControllerCallback) {
        dataControllerGroup.deregisterForCallbacks(dataControllerCallback);
    }

    /**
     * Cancels any pending requests and then re-requests data using the {@link DataFetcher}.
     *
     * @return Stored data.
     */
    public TResponse requestDataCancel() {
        cancel();
        return requestData();
    }

    /**
     * The standard method for execution, this method retrieves any fast-access data from the {@link IDataStore}.
     * We then request data in the background that will come in asynchronously.
     *
     * @return Stored data via {@link #getStoredData()}.
     * @see #requestDataAsync()
     */
    public TResponse requestData() {
        TResponse response = getStoredData();
        requestDataAsync();
        return response;
    }

    /**
     * Requests data from the {@link DataFetcher}.
     *
     * @return true if we successfully requested data, false if its {@link State#LOADING} or {@link IRefreshStrategy}
     * disallows requesting.
     */
    public boolean requestDataAsync() {
        if (!state.equals(State.LOADING) && (refreshStrategy == null || refreshStrategy.shouldRefresh(this))) {
            setState(State.LOADING);
            dataControllerGroup.onStartLoading();
            requestDataForce();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Directly calls the {@link DataFetcher} with no regards to state or strategy.
     */
    public final void requestDataForce() {
        getDataFetcher().call();
    }

    /**
     * Clears out any stored data via {@link IDataStore}, if it exists.
     */
    public void clearStoredData() {
        if (dataStore != null) {
            dataStore.clear();
        }
    }

    /**
     * @param <T> the type of {@link IRefreshStrategy} you wish to return.
     * @return Unsafely casts the underlying strategy to the return type.
     */
    @SuppressWarnings("unchecked")
    public <T extends IRefreshStrategy> T getRefreshStrategy() {
        return (T) refreshStrategy;
    }

    /**
     * Cancels any current running requests. It does not report its state to the {@link DataControllerCallbackGroup}.
     */
    public void cancel() {
        getDataFetcher().cancel();
        setState(State.NONE);
    }

    /**
     * Clears stored data and will set its state back to {@link State#NONE}.
     */
    public void close() {
        cancel();
        clearStoredData();
        dataControllerGroup.onClosed();
    }

    /**
     * Closes if no {@link IDataControllerCallback} exists for it. Such that we don't unnecessary clear
     * and remove data if its still referenced somewhere else.
     */
    public void closeIfNecessary() {
        if (dataControllerGroup.isEmpty()) {
            close();
        }
    }

    /**
     * Called when we're going to store the response.
     *
     * @param response The response that we received.
     */
    protected void storeResponseData(TResponse response) {
        if (dataStore != null) {
            dataStore.store(response);
        }
    }

    /**
     * @param response The response directly from our {@link DataFetcher}.
     * @return whether a successful {@link TResponse} should be considered empty. Be careful as returning
     * true will cause {@link IDataControllerCallback#onEmpty()} to get invoked.
     */
    public boolean isEmpty(TResponse response) {
        return emptyChecker != null && emptyChecker.isEmpty(response);
    }

    public DataFetcher<TResponse> getDataFetcher() {
        if (dataFetcher == null) {
            throw new IllegalStateException("You must define a DataFetcher for this DataController");
        }
        return dataFetcher;
    }

    /**
     * Defines how it fetches data. This is required.
     */
    public void setDataFetcher(DataFetcher<TResponse> dataFetcher) {
        this.dataFetcher = dataFetcher;
    }

    /**
     * Defines how data gets refreshed. Not required, by default it will always trigger an update if
     * it is not {@link State#LOADING}
     */
    public void setRefreshStrategy(IRefreshStrategy refreshStrategy) {
        this.refreshStrategy = refreshStrategy;
    }

    /**
     * Sets what {@link IDataStore} this datacontroller uses. This is usually invoked in the constructor.
     * Not required, by default we do not store any leftover data.
     *
     * @param dataStore The dataStore to use.
     */
    public void setDataStore(IDataStore<TResponse> dataStore) {
        this.dataStore = dataStore;
    }

    /**
     * @return The stored data from the {@link IDataStore}. It serves as a "fast-access" pipeline. So
     * any data that is readily available and not usually IO or network should get returned here.
     */
    public TResponse getStoredData() {
        return dataStore != null ? dataStore.get() : null;
    }

    public IDataStore<TResponse> getDataStore() {
        return dataStore;
    }

    /**
     * Sets the current state on this DC.
     *
     * @param state The state to set.
     */
    protected synchronized void setState(State state) {
        this.state = state;
    }

    /**
     * @return The current state of this DC.
     */
    public State getState() {
        return state;
    }

    /**
     * @return The {@link IDataCallback} handle that is contained here.
     */
    public IDataCallback<DataControllerResponse<TResponse>> getDataCallback() {
        return dataCallback;
    }

    /**
     * Called when we got a successful callback.
     *
     * @param response   The response received.
     * @param requestUrl The url that was requested.
     */
    protected void onSuccessfulResponse(DataControllerResponse<TResponse> response, String requestUrl) {
        storeResponseData(response.getResponse());
        if (isEmpty(response.getResponse())) {
            setState(State.EMPTY);
            dataControllerGroup.onEmpty();
        } else {
            setState(State.SUCCESS);
            dataControllerGroup.onSuccess(response, requestUrl);
        }
    }

    /**
     * The failure we received.
     *
     * @param error The error that was received.
     */
    protected final void onFailure(DataResponseError error) {
        setState(State.FAILURE);
        dataControllerGroup.onFailure(error);
    }


    private final IDataCallback<DataControllerResponse<TResponse>> dataCallback = new IDataCallback<DataControllerResponse<TResponse>>() {
        @Override
        public void onSuccess(DataControllerResponse<TResponse> tResponse, String originalUrl) {
            DataController.this.onSuccessfulResponse(tResponse, originalUrl);
        }

        @Override
        public void onFailure(DataResponseError dataResponseError) {
            DataController.this.onFailure(dataResponseError);
        }
    };

}
