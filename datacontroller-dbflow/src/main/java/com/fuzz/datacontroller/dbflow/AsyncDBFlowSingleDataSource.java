package com.fuzz.datacontroller.dbflow;

import android.support.annotation.Nullable;

import com.fuzz.datacontroller.DataController;
import com.fuzz.datacontroller.DataControllerResponse;
import com.fuzz.datacontroller.source.DataSource2;
import com.raizlabs.android.dbflow.structure.Model;
import com.raizlabs.android.dbflow.structure.database.transaction.ProcessModelTransaction;
import com.raizlabs.android.dbflow.structure.database.transaction.QueryTransaction;

/**
 * Description: Loads and saves a single {@link TModel} to/from the database.
 * Provides data from the DB when used.
 */
public class AsyncDBFlowSingleDataSource<TModel extends Model>
        extends BaseAsyncDBFlowDataSource<TModel, TModel> {

    public AsyncDBFlowSingleDataSource(DataSource2.RefreshStrategy<TModel> refreshStrategy,
                                       Class<TModel> tModelClass) {
        super(refreshStrategy, tModelClass);
    }

    public AsyncDBFlowSingleDataSource(Class<TModel> tModelClass) {
        super(tModelClass);
    }

    public AsyncDBFlowSingleDataSource(DataSource2.RefreshStrategy<TModel> refreshStrategy,
                                       DBFlowParamsInterface<TModel> defaultParams) {
        super(refreshStrategy, defaultParams);
    }

    public AsyncDBFlowSingleDataSource(DBFlowParamsInterface<TModel> defaultParams) {
        super(defaultParams);
    }

    @Override
    public TModel getStoredData(SourceParams sourceParams) {
        return getParams(sourceParams).getModelQueriable().querySingle();
    }

    @Override
    protected void prepareQuery(QueryTransaction.Builder<TModel> queryBuilder,
                                final DataController.Success<TModel> success) {
        queryBuilder.querySingleResult(new QueryTransaction.QueryResultSingleCallback<TModel>() {
            @Override
            public void onSingleQueryResult(QueryTransaction transaction,
                                            @Nullable TModel model) {
                success.onSuccess(new DataControllerResponse<>(model, getSourceType()));
            }
        });
    }

    @Override
    protected void prepareStore(ProcessModelTransaction.Builder<TModel> processBuilder,
                                DataControllerResponse<TModel> response) {
        if (response.getResponse() != null) {
            processBuilder.add(response.getResponse());
        }
    }
}
