package com.arvatar.vortex.models;

import java.sql.Timestamp;

public class PersonProfile {
        public final String guruId;
        public final String displayName;
        public final String biography;
        public final Timestamp createdAt;

        public PersonProfile(String guruId, String displayName, String biography, Timestamp createdAt) {
            this.guruId = guruId;
            this.displayName = displayName;
            this.biography = biography;
            this.createdAt = createdAt;
        }
    }
