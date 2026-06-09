package uk.co.community.imagebrowser.repository;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import uk.co.community.imagebrowser.model.ImageRecord;
import uk.co.community.imagebrowser.model.ImageSummary;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ImageRepository")
class ImageRepositoryTest {

    private ImageRepository repository;
    private SingleConnectionDataSource ds;

    @BeforeEach
    void setUp() {
        // In-memory SQLite, one shared connection per test. A plain
        // DriverManagerDataSource opens a new connection per operation, and each
        // ":memory:" connection is a separate database — so the schema created on
        // one connection is invisible to the next. SingleConnectionDataSource reuses
        // a single physical connection, keeping the in-memory DB alive for the test.
        ds = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        ds.setDriverClassName("org.sqlite.JDBC");

        var jdbc = new JdbcTemplate(ds);
        repository = new ImageRepository(jdbc);
        repository.createSchema();
    }

    @AfterEach
    void tearDown() {
        ds.destroy();
    }

    // ---------------------------------------------------------------
    // Insert + retrieval
    // ---------------------------------------------------------------

    @Test
    @DisplayName("insert returns a positive generated id")
    void insert_WhenRecordInserted_ShouldReturnPositiveGeneratedId() {
        long id = repository.insert(testRecord("test.jpg"));
        assertThat(id).isGreaterThan(0);
    }

    @Test
    @DisplayName("findSummaryById returns inserted record")
    void findSummaryById_WhenIdExists_ShouldReturnRecord() {
        long id = repository.insert(testRecord("canal.jpg"));

        var result = repository.findSummaryById(id);

        assertThat(result).isPresent();
        assertThat(result.get().filename()).isEqualTo("canal.jpg");
        assertThat(result.get().sheetName()).isEqualTo("Sheet1");
    }

    @Test
    @DisplayName("findSummaryById returns empty for unknown id")
    void findSummaryById_WhenIdUnknown_ShouldReturnEmpty() {
        assertThat(repository.findSummaryById(999L)).isEmpty();
    }

    @Test
    @DisplayName("findThumbnailById returns thumbnail bytes")
    void findThumbnailById_WhenIdExists_ShouldReturnThumbnailBytes() {
        long id = repository.insert(testRecord("thumb.jpg"));
        var thumb = repository.findThumbnailById(id);
        assertThat(thumb).isPresent();
        assertThat(thumb.get()).isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    @DisplayName("findFullImageById returns full image bytes")
    void findFullImageById_WhenIdExists_ShouldReturnFullImageBytes() {
        long id = repository.insert(testRecord("full.jpg"));
        var full = repository.findFullImageById(id);
        assertThat(full).isPresent();
        assertThat(full.get()).isEqualTo(new byte[]{4, 5, 6, 7});
    }

    // ---------------------------------------------------------------
    // Search
    // ---------------------------------------------------------------

    @Test
    @DisplayName("findAll returns all inserted records")
    void findAll_WhenRecordsExist_ShouldReturnAllRecords() {
        repository.insert(testRecord("a.jpg"));
        repository.insert(testRecord("b.jpg"));
        repository.insert(testRecord("c.jpg"));

        List<ImageSummary> results = repository.findAll(0, 100);

        assertThat(results).hasSize(3);
    }

    @Test
    @DisplayName("findAll respects limit and offset")
    void findAll_WhenLimitAndOffsetGiven_ShouldRespectPagination() {
        repository.insert(testRecord("a.jpg"));
        repository.insert(testRecord("b.jpg"));
        repository.insert(testRecord("c.jpg"));

        List<ImageSummary> page1 = repository.findAll(0, 2);
        List<ImageSummary> page2 = repository.findAll(2, 2);

        assertThat(page1).hasSize(2);
        assertThat(page2).hasSize(1);
    }

    @Test
    @DisplayName("search by filename prefix returns matching records")
    void search_WhenFilenamePrefixMatches_ShouldReturnMatchingRecords() {
        repository.insert(testRecordWithMeta("canal-boat.jpg", "canal boat", "narrow boat"));
        repository.insert(testRecordWithMeta("river.jpg", "river", "river scene"));

        List<ImageSummary> results = repository.search("canal", 0, 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).filename()).isEqualTo("canal-boat.jpg");
    }

    @Test
    @DisplayName("search returns empty list for no matches")
    void search_WhenNoMatches_ShouldReturnEmptyList() {
        repository.insert(testRecord("canal.jpg"));

        List<ImageSummary> results = repository.search("zzznonexistent", 0, 10);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("empty search returns all records")
    void search_WhenQueryEmpty_ShouldReturnAllRecords() {
        repository.insert(testRecord("a.jpg"));
        repository.insert(testRecord("b.jpg"));

        List<ImageSummary> results = repository.search("", 0, 100);

        assertThat(results).hasSize(2);
    }

    // ---------------------------------------------------------------
    // Navigation
    // ---------------------------------------------------------------

    @Test
    @DisplayName("findFirstId returns smallest id")
    void findFirstId_WhenRecordsExist_ShouldReturnSmallestId() {
        long id1 = repository.insert(testRecord("a.jpg"));
        repository.insert(testRecord("b.jpg"));

        assertThat(repository.findFirstId()).contains(id1);
    }

    @Test
    @DisplayName("findLastId returns largest id")
    void findLastId_WhenRecordsExist_ShouldReturnLargestId() {
        repository.insert(testRecord("a.jpg"));
        long id2 = repository.insert(testRecord("b.jpg"));

        assertThat(repository.findLastId()).contains(id2);
    }

    @Test
    @DisplayName("findNextId returns the next id in sequence")
    void findNextId_WhenNotLast_ShouldReturnNextIdInSequence() {
        long id1 = repository.insert(testRecord("a.jpg"));
        long id2 = repository.insert(testRecord("b.jpg"));

        assertThat(repository.findNextId(id1)).contains(id2);
    }

    @Test
    @DisplayName("findPrevId returns the previous id in sequence")
    void findPrevId_WhenNotFirst_ShouldReturnPreviousIdInSequence() {
        long id1 = repository.insert(testRecord("a.jpg"));
        long id2 = repository.insert(testRecord("b.jpg"));

        assertThat(repository.findPrevId(id2)).contains(id1);
    }

    @Test
    @DisplayName("findNextId returns empty for last record")
    void findNextId_WhenLastRecord_ShouldReturnEmpty() {
        repository.insert(testRecord("a.jpg"));
        long id2 = repository.insert(testRecord("b.jpg"));

        assertThat(repository.findNextId(id2)).isEmpty();
    }

    @Test
    @DisplayName("findFirstId and findLastId return empty on empty DB")
    void findFirstIdAndFindLastId_WhenDbEmpty_ShouldReturnEmpty() {
        assertThat(repository.findFirstId()).isEmpty();
        assertThat(repository.findLastId()).isEmpty();
    }

    // ---------------------------------------------------------------
    // Count
    // ---------------------------------------------------------------

    @Test
    @DisplayName("count returns 0 for empty database")
    void count_WhenDbEmpty_ShouldReturnZero() {
        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("count returns correct number after inserts")
    void count_WhenRecordsInserted_ShouldReturnCorrectNumber() {
        repository.insert(testRecord("a.jpg"));
        repository.insert(testRecord("b.jpg"));
        assertThat(repository.count()).isEqualTo(2);
    }

    // ---------------------------------------------------------------
    // App config
    // ---------------------------------------------------------------

    @Test
    @DisplayName("setConfig and getConfig round-trip")
    void setConfigAndGetConfig_WhenValueStored_ShouldRoundTrip() {
        repository.setConfig("last_updated", "2026-05-22T10:00:00");
        assertThat(repository.getConfig("last_updated"))
                .contains("2026-05-22T10:00:00");
    }

    @Test
    @DisplayName("setConfig is idempotent (upsert)")
    void setConfig_WhenKeyExists_ShouldUpsertValue() {
        repository.setConfig("key", "v1");
        repository.setConfig("key", "v2");
        assertThat(repository.getConfig("key")).contains("v2");
    }

    @Test
    @DisplayName("getConfig returns empty for unknown key")
    void getConfig_WhenKeyUnknown_ShouldReturnEmpty() {
        assertThat(repository.getConfig("nonexistent")).isEmpty();
    }

    // ---------------------------------------------------------------
    // Clear all
    // ---------------------------------------------------------------

    @Test
    @DisplayName("clearAll removes all images and config")
    void clearAll_WhenInvoked_ShouldRemoveAllImagesAndConfig() {
        repository.insert(testRecord("a.jpg"));
        repository.insert(testRecord("b.jpg"));
        repository.setConfig("last_updated", "2026-05-01");

        repository.clearAll();

        assertThat(repository.count()).isZero();
        assertThat(repository.getConfig("last_updated")).isEmpty();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private ImageRecord testRecord(String filename) {
        return new ImageRecord(0L, filename, "Sheet1", "R0C0",
                "image/jpeg",
                new byte[]{4, 5, 6, 7},
                new byte[]{1, 2, 3},
                300, 200, "", "");
    }

    private ImageRecord testRecordWithMeta(String filename, String tags, String description) {
        return new ImageRecord(0L, filename, "Sheet1", "R0C0",
                "image/jpeg",
                new byte[]{4, 5, 6, 7},
                new byte[]{1, 2, 3},
                300, 200, tags, description);
    }
}
