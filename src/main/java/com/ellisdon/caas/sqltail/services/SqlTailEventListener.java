package com.ellisdon.caas.sqltail.services;

import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SqlTailEventListener implements BinaryLogClient.EventListener {

    HashMap<Long, String> tableNameMap = new HashMap<>();

    @Override
    public void onEvent(Event event) {
        EventType eventType = event.getHeader().getEventType();
        switch (eventType) {
            case TABLE_MAP:
                TableMapEventData tableMapEventData = event.getData();
                // handle tableMapEventData (contains mapping between *RowsEventData::tableId and table name
                // that can be used (together with https://github.com/shyiko/mysql-binlog-connector-java/issues/24#issuecomment-43747417)
                // to map column names to the values)
                tableNameMap.put(tableMapEventData.getTableId(), tableMapEventData.getTable());
                break;
            case PRE_GA_WRITE_ROWS:
            case WRITE_ROWS:
            case EXT_WRITE_ROWS:
                // handle writeRowsEventData (generated when someone INSERTs data)
                WriteRowsEventData writeRowsEventData = event.getData();
                for (Serializable[] row : writeRowsEventData.getRows()) {
                    log.info("Inserting into {} with {}", tableNameMap.get(writeRowsEventData.getTableId()), handleSerializableRow(row).toString());
                }
                break;
            case PRE_GA_UPDATE_ROWS:
            case UPDATE_ROWS:
            case EXT_UPDATE_ROWS:
                // handle updateRowsEventData (generated when someone UPDATEs data)
                UpdateRowsEventData updateRowsEventData = event.getData();
                for (Map.Entry<Serializable[], Serializable[]> row : updateRowsEventData.getRows()) {
                    log.info("Updating {} with {}", tableNameMap.get(updateRowsEventData.getTableId()), handleSerializableRow(row.getValue()).toString());
                }
                break;
            case PRE_GA_DELETE_ROWS:
            case DELETE_ROWS:
            case EXT_DELETE_ROWS:
                // handle deleteRowsEventData (generated when someone DELETEs data)
                DeleteRowsEventData deleteRowsEventData = event.getData();
                for (Serializable[] row : deleteRowsEventData.getRows()) {
                    log.info("Deleting from {} with {}", tableNameMap.get(deleteRowsEventData.getTableId()), handleSerializableRow(row).toString());
                }
                break;
        }
    }

    private List<String> handleSerializableRow(Serializable[] row) {
        return Arrays.stream(row).map(c -> {
                    String value;
                    if (c instanceof byte[]) {
                        value = new String((byte[]) c, StandardCharsets.UTF_8);
                    } else {
                        value = String.valueOf(c);
                    }
                    return value;
                }
        ).collect(Collectors.toList());
    }
}
