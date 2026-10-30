package com.kovanlabs.librarymanagement.database.enums;

/**
 * Enum representing the supported book categories in the library catalog.
 */
public enum BookCategory {

    FICTION("Fiction"),
    NON_FICTION("Non-Fiction"),
    TECHNOLOGY("Technology"),
    SCIENCE("Science"),
    HISTORY("History"),
    BIOGRAPHY("Biography"),
    PHILOSOPHY("Philosophy"),
    MYSTERY_THRILLER("Mystery & Thriller"),
    SELF_HELP("Self-Help"),
    CHILDREN("Children");

    private final String displayName;

    BookCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Resolves a string value (either enum name or display name) to a {@link BookCategory}.
     *
     * @param value String value
     * @return matching {@link BookCategory} or null if value is null
     */
    public static BookCategory fromValue(String value) {
        if (value == null) return null;
        for (BookCategory category : values()) {
            if (category.name().equalsIgnoreCase(value) || category.displayName.equalsIgnoreCase(value)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown book category: " + value);
    }
}
