package com.github.griga23;

public class Event {

    private String key;
    private Integer value;

    private Event(String key, Integer value) {
        this.key = key;
        this.value = value;
    }

    public static Event of(String key, Integer value){
        return new Event(key,value);
    }

    public String getKey() {
        return key;

    }

    public Integer getValue() {
        return value;
    }
}
