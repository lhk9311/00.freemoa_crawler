package com.lhk.freemoa.nexacro;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 넥사크로 Dataset 하나 (컬럼 정의 + 행 목록).
 *
 * 사용 예)
 *   NxDataset ds = new NxDataset("ds_list")
 *           .column("projIdx", NxType.INT, 10)
 *           .column("title", NxType.STRING, 500);
 *   ds.addRow(48588, "PHP 버전 업데이트");
 *
 * 값은 컬럼을 선언한 순서대로 넣는다. 개수가 다르면 바로 예외 -> 잘못된 데이터가 화면까지 가는 걸 막는다.
 */
public class NxDataset {

    public record Column(String id, NxType type, int size) {
    }

    private final String id;
    private final List<Column> columns = new ArrayList<>();
    private final List<Object[]> rows = new ArrayList<>();

    public NxDataset(String id) {
        this.id = id;
    }

    public NxDataset column(String columnId, NxType type, int size) {
        if (!rows.isEmpty()) {
            throw new IllegalStateException("행을 넣은 뒤에는 컬럼을 추가할 수 없음: " + columnId);
        }
        columns.add(new Column(columnId, type, size));
        return this; // 메서드 체이닝
    }

    public NxDataset addRow(Object... values) {
        if (values.length != columns.size()) {
            throw new IllegalArgumentException(
                    "컬럼 수(" + columns.size() + ")와 값 수(" + values.length + ")가 다름: " + id);
        }
        rows.add(Arrays.copyOf(values, values.length)); // 방어적 복사
        return this;
    }

    public String id() {
        return id;
    }

    public List<Column> columns() {
        return Collections.unmodifiableList(columns);
    }

    public List<Object[]> rows() {
        return Collections.unmodifiableList(rows);
    }
}
