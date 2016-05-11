package com.fuzz.datacontroller;

import com.fuzz.datacontroller.source.DataSource;
import com.fuzz.datacontroller.source.DataSource.SourceType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Description: Provides basic implementation of a data controller.
 */
public class DataController<TResponse> {

    /**
     * Description: Represents a failed callback response.
     */
    public interface Error {

        void onFailure(DataResponseError dataResponseError);
    }

    /**
     * Description: Represents a successful execution.
     */
    public interface Success<TResponse> {

        void onSuccess(DataControllerResponse<TResponse> response);
    }

    /**
     * The main callback interface for getting callbacks on the the {@link DataController} class.
     *
     * @param <TResponse>
     */
    public interface DataControllerCallback<TResponse> extends Error, Success<TResponse> {
    }

    private final Map<SourceType, DataSource<TResponse>>
            dataSourceMap = new TreeMap<>();

    private final DataControllerCallbackGroup<TResponse> callbackGroup = new DataControllerCallbackGroup<>();

    public void registerDataSource(DataSource<TResponse> dataSource) {
        synchronized (dataSourceMap) {
            dataSourceMap.put(dataSource.getSourceType(), dataSource);
        }
    }

    public void deregisterDataSource(SourceType sourceType) {
        synchronized (dataSourceMap) {
            dataSourceMap.remove(sourceType);
        }
    }

    public void registerForCallbacks(DataControllerCallback<TResponse> dataControllerCallback) {
        callbackGroup.registerForCallbacks(dataControllerCallback);
    }

    public void deregisterForCallbacks(DataControllerCallback<TResponse> dataControllerCallback) {
        callbackGroup.deregisterForCallbacks(dataControllerCallback);
    }

    public void clearCallbacks() {
        callbackGroup.clearCallbacks();
    }

    /**
     * Requests data with default parameters.
     */
    public void requestData() {
        requestData(new DataSource.SourceParams());
    }

    /**
     * Requests data from each of the {@link DataSource} here, passing in a sourceParams object.
     * It will iterate through all sources and call each one.
     *
     * @param sourceParams The params to use for a query.
     */
    public void requestData(DataSource.SourceParams sourceParams) {
        synchronized (dataSourceMap) {
            Collection<DataSource<TResponse>> sourceCollection = dataSourceMap.values();
            for (DataSource<TResponse> source : sourceCollection) {
                source.get(sourceParams, internalSuccessCallback, internalErrorCallback);
            }
        }
    }

    /**
     * Requests a specific source with specified params.
     *
     * @param sourceType   The type of source to request via {@link SourceType}
     * @param sourceParams The params used in the request.
     */
    public void requestSpecific(SourceType sourceType, DataSource.SourceParams sourceParams) {
        DataSource<TResponse> dataSource = dataSourceMap.get(sourceType);
        if (dataSource == null) {
            throw new RuntimeException("No data source found for type: " + sourceType);
        }

        dataSource.get(sourceParams, internalSuccessCallback, internalErrorCallback);
    }

    /**
     * Cancels all attached {@link DataSource}.
     */
    public void cancel() {
        synchronized (dataSourceMap) {
            Collection<DataSource<TResponse>> sourceCollection = dataSourceMap.values();
            for (DataSource<TResponse> source : sourceCollection) {
                source.cancel();
            }
        }
    }

    public List<DataSource<TResponse>> getSources() {
        return new ArrayList<>(dataSourceMap.values());
    }

    public DataSource<TResponse> getSource(SourceType sourceType) {
        return dataSourceMap.get(sourceType);
    }


    private final Success<TResponse> internalSuccessCallback = new Success<TResponse>() {
        @Override
        public void onSuccess(DataControllerResponse<TResponse> response) {
            Collection<DataSource<TResponse>> dataSources = dataSourceMap.values();
            for (DataSource<TResponse> dataSource : dataSources) {
                dataSource.store(response);
            }

            callbackGroup.onSuccess(response);
        }
    };

    private final Error internalErrorCallback = new Error() {
        @Override
        public void onFailure(DataResponseError dataResponseError) {
            callbackGroup.onFailure(dataResponseError);
        }
    };


}
