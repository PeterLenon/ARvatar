package com.arvatar.vortex.models;

public enum Intention {
    AWAKEN,
    CONVERSE,
    SLEEP,
    VORTEX,
    NEW_GURU,
    VOLUME_UP,
    VOLUME_DOWN,
    BRIGHTNESS_UP,
    BRIGHTNESS_DOWN,
    SPEAK_FAST,
    SPEAK_SLOW;

    public static String toString(Intention intention){
        if(intention == AWAKEN) return "AWAKE";
        if(intention == CONVERSE) return "CONVERSE";
        if(intention == SLEEP) return "SLEEP";
        if(intention == VORTEX) return "VORTEX";
        if(intention == NEW_GURU) return "NEW_GURU";
        if(intention == VOLUME_UP) return "VOLUME_UP";
        if(intention == VOLUME_DOWN) return "VOLUME_DOWN";
        if(intention == BRIGHTNESS_UP) return "BRIGHTNESS_UP";
        if(intention == BRIGHTNESS_DOWN) return "BRIGHTNESS_DOWN";
        if(intention == SPEAK_FAST) return "SPEAK_FAST";
        if(intention == SPEAK_SLOW) return "SPEAK_SLOW";
        return null;
    }
}
