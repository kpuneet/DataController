package com.fuzz.datacontroller.test;

import com.fuzz.datacontroller.source.DataSource;
import com.fuzz.datacontroller.source.DataSourceStorage.DataSourceParams;
import com.fuzz.datacontroller.source.ListBasedDataSourceContainer;
import com.fuzz.datacontroller.source.MemoryDataSource;
import com.fuzz.datacontroller.source.TreeMapSingleTypeDataSourceContainer;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Description:
 */
public class DefaultDataSourceContainerTest {

    private DataSource<String> mockDiskDataSource;
    private DataSource<String> mockNetworkDataSource;

    @Before
    public void setup_test() {
        mockDiskDataSource = mock(DataSource.class);
        when(mockDiskDataSource.getSourceType()).thenReturn(DataSource.SourceType.DISK);
        mockNetworkDataSource = mock(DataSource.class);
        when(mockNetworkDataSource.getSourceType()).thenReturn(DataSource.SourceType.NETWORK);
    }

    @Test
    public void test_treeMapDataSourceContainer() {
        TreeMapSingleTypeDataSourceContainer<String> treeMapDataSourceContainer
                = new TreeMapSingleTypeDataSourceContainer<>();

        MemoryDataSource<String> memoryDataSource = new MemoryDataSource<>();
        treeMapDataSourceContainer.registerDataSource(memoryDataSource);
        treeMapDataSourceContainer.registerDataSource(mockDiskDataSource);
        treeMapDataSourceContainer.registerDataSource(mockNetworkDataSource);

        assertNotNull(treeMapDataSourceContainer.getDataSource(DataSourceParams.memoryParams()));

        assertEquals(3, treeMapDataSourceContainer.sources().size());

        treeMapDataSourceContainer.deregisterDataSource(memoryDataSource);
        assertEquals(2, treeMapDataSourceContainer.sources().size());
    }

    @Test
    public void test_ListBasedDataSourceContainer() {
        ListBasedDataSourceContainer<String> listBasedDataSourceContainer
                = new ListBasedDataSourceContainer<>();

        listBasedDataSourceContainer.registerDataSource(mockDiskDataSource);
        listBasedDataSourceContainer.registerDataSource(mock(DataSource.class));

        assertEquals(2, listBasedDataSourceContainer.sources().size());

        boolean failed = false;
        try {
            listBasedDataSourceContainer.getDataSource(DataSourceParams.memoryParams());
        } catch (ArrayIndexOutOfBoundsException e) {
            failed = true;
        }
        assertTrue(failed);

        DataSource<String> dataSource = listBasedDataSourceContainer.getDataSource(new DataSourceParams(0));
        assertEquals(dataSource, mockDiskDataSource);

        assertNotEquals(listBasedDataSourceContainer.getDataSource(new DataSourceParams(1)), mockDiskDataSource);

        listBasedDataSourceContainer.deregisterDataSource(mockDiskDataSource);
        assertEquals(1, listBasedDataSourceContainer.sources().size());
    }
}
