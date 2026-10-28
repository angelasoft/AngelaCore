package com.angelasoft.presidium.ledger;

/** 지출 항목. 장부 하단 지출 내역(의연금·꽃·초값·기타) 집계에 쓴다. */
public enum ExpenseCategory {
    FLOWER("꽃 값"),
    DONATION("의연금"),
    OTHER("기타");

    private final String label;

    ExpenseCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
