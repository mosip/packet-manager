package io.mosip.commons.packet.constants;

/**
 * Accepted values for the {@code type} filter in tag-retrieval requests.
 *
 * <ul>
 *   <li>{@link #ANONYMOUS} – return only tags whose name starts with the
 *       anonymous prefix ({@code ANONYMOUS}).</li>
 *   <li>{@link #ALL} – return every tag stored for the packet.</li>
 * </ul>
 *
 * When {@code type} is {@code null} or blank the default behaviour applies:
 * all tags are returned <em>except</em> anonymous ones (privacy protection).
 */
public enum TagType {

    ANONYMOUS("anonymous"),
    ALL("all");

    private final String value;

    TagType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
