package com.angelasoft.presidium;

/**
 * 쁘레시디움 구분. 한 사람은 어른 쁘레시디움에 하나만 가입할 수 있고,
 * 소년소녀 쁘레시디움은 이끌어 주는 어른이 함께 가입하므로 여러 곳에 가입할 수 있다.
 */
public enum PresidiumType {
    ADULT("어른"),
    JUNIOR("소년소녀");

    private final String label;

    PresidiumType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
