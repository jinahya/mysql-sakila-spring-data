package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Film;
import com.github.jinahya.mysql.sakila.persistence.Inventory;
import com.github.jinahya.mysql.sakila.persistence.Inventory_;
import com.github.jinahya.mysql.sakila.persistence.Store;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link InventoryRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is a few copies of the
 * sample data, selected once in {@link #__()}, and each test checks a query of the exercises against them, against
 * counts of the copies taken another way, or against the output upstream's {@code inventory_exercises_*.sql} records
 * for the installed data. The base's own tests, such as its {@code findById} round trips, run here too.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @see InventoryRepository_DataJpaTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InventoryRepository_SpringBootIT
        extends _Repository_SpringBootIT<InventoryRepository, Inventory, Integer> {

    private static List<Inventory> entities;

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code Inventory_.inventoryId} is filled in only
     * once the context is up, after this class is loaded.
     */
    private static final Sort BY_INVENTORY_ID = Sort.by(Sort.Direction.ASC, Inventory_.INVENTORY_ID);

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance.
     */
    InventoryRepository_SpringBootIT() {
        super(InventoryRepository.class, Inventory.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Selects the first five copies, by id, as the fixture for the parameterized tests.
     * <p>
     * Ordered by id so that the rows are the ones the init scripts put there. Runs once per class, which
     * {@link TestInstance.Lifecycle#PER_CLASS} allows to be an instance method, so the autowired repository is already
     * there; the result goes into a static field so that the static method sources of the nested classes can reach it.
     * The copies are loaded outside any transaction, so they are detached.
     * <p>
     * An empty table aborts the tests of this class rather than failing them: it is the fixture that is missing, not
     * the behaviour under test that is wrong.
     */
    @BeforeAll
    void __() {
        entities = Collections.unmodifiableList(
                repositoryInstance().findAll(PageRequest.of(0, 5, BY_INVENTORY_ID)).getContent()
        );
        assumeThat(entities).isNotEmpty();
    }

    private static Stream<Inventory> inventories() {
        return entities.stream();
    }

    /**
     * Counts the copies of the store of the specified id, through a {@link Specification} on the read-only
     * {@link Inventory_#storeId}.
     *
     * @param storeId the id of the store.
     * @return the number of copies of the store.
     */
    private long countByStoreId(final Integer storeId) {
        final Specification<Inventory> specification = (r, q, b) -> b.equal(r.get(Inventory_.storeId), storeId);
        return repositoryInstance().count(specification);
    }

    /**
     * Counts the copies of the film of the specified id, through a {@link Specification} on the read-only
     * {@link Inventory_#filmId}.
     *
     * @param filmId the id of the film.
     * @return the number of copies of the film.
     */
    private long countByFilmId(final Integer filmId) {
        final Specification<Inventory> specification = (r, q, b) -> b.equal(r.get(Inventory_.filmId), filmId);
        return repositoryInstance().count(specification);
    }

    /**
     * Asserts that the specified page is consistent with itself: no more content than its size, and at least as many
     * elements in all as in its content.
     *
     * @param page the page.
     * @param <T>  the type of the elements.
     */
    private static <T> void assertPage(final Page<T> page) {
        assertThat(page.getContent()).hasSizeLessThanOrEqualTo(page.getSize());
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(page.getNumberOfElements());
    }

    // ----------------------------------------------------------------------------------------------------- exercises

    /**
     * Tests {@link InventoryRepository#selectCopyCountsByStore()} ({@code SAKILA-INVENTORY-B-01}).
     */
    @Nested
    class SelectCopyCountsByStore_Test {

        private static Stream<Inventory> inventories() {
            return InventoryRepository_SpringBootIT.inventories();
        }

        /**
         * Asserts that the list is the exercise's recorded output, the two stores by id with 2270 and 2311 copies,
         * that each count is the number of copies of the store, and that the counts add up to all copies.
         */
        @Test
        void __() {
            final var list = repositoryInstance().selectCopyCountsByStore();
            assertThat(list)
                    .extracting(column("store", Store.class, Store::getStoreId), column("copies", Long.class))
                    .containsExactly(tuple(1, 2270L), tuple(2, 2311L));
            assertThat(list).allSatisfy(c -> {
                final var store = c.get("store", Store.class);
                assertThat(c.get("copies", Long.class)).as("copies of store %d", store.getStoreId())
                        .isEqualTo(countByStoreId(store.getStoreId()));
            });
            final var sum = list.stream().mapToLong(c -> c.get("copies", Long.class)).sum();
            assertThat(sum).isEqualTo(repositoryInstance().count());
        }

        /**
         * Asserts that the list is ordered by store id, as the query orders it.
         */
        @Test
        void __Ordered() {
            final var list = repositoryInstance().selectCopyCountsByStore();
            assertThat(list).isSortedAccordingTo(Comparator.comparing(column("store", Store.class, Store::getStoreId)));
        }

        /**
         * Asserts that the store of a selected copy is among the stores selected.
         *
         * @param inventory one of the copies selected in {@link InventoryRepository_SpringBootIT#__()}.
         */
        @MethodSource("inventories")
        @ParameterizedTest
        void __Selected(final Inventory inventory) {
            final var list = repositoryInstance().selectCopyCountsByStore();
            assertThat(list)
                    .extracting(column("store", Store.class, Store::getStoreId))
                    .contains(inventory.getStoreId());
        }
    }

    /**
     * Tests {@link InventoryRepository#findAllByFilmIdOrderByInventoryIdAsc(Integer)} ({@code SAKILA-INVENTORY-B-02}).
     */
    @Nested
    class FindAllByFilmIdOrderByInventoryIdAsc_Test {

        private static Stream<Inventory> inventories() {
            return InventoryRepository_SpringBootIT.inventories();
        }

        /**
         * Asserts, in one transaction, that the film {@code 1} streams the exercise's recorded output, the copies
         * {@code 1} to {@code 8}, the first four of the store {@code 1} and the rest of the store {@code 2}.
         */
        @Test
        @Transactional
        void __Transactional() {
            try (var found = repositoryInstance().findAllByFilmIdOrderByInventoryIdAsc(1)) {
                assertThat(found.toList())
                        .extracting(Inventory::getInventoryId, Inventory::getStoreId)
                        .containsExactly(tuple(1, 1), tuple(2, 1), tuple(3, 1), tuple(4, 1),
                                         tuple(5, 2), tuple(6, 2), tuple(7, 2), tuple(8, 2));
            }
        }

        /**
         * Asserts, in one transaction, that the film of a selected copy streams it, as the instance managed in the
         * transaction, among copies of that film only, by id, as many as a {@link Specification} counts.
         *
         * @param inventory one of the copies selected in {@link InventoryRepository_SpringBootIT#__()}.
         */
        @MethodSource("inventories")
        @ParameterizedTest
        @Transactional
        void __Ordered(final Inventory inventory) {
            final var managed = repositoryInstance().findById(inventory.getInventoryId()).orElseThrow();
            try (var found = repositoryInstance().findAllByFilmIdOrderByInventoryIdAsc(managed.getFilmId())) {
                final var list = found.toList();
                assertThat(list)
                        .allSatisfy(i -> assertThat(i.getFilmId()).isEqualTo(managed.getFilmId()))
                        .isSortedAccordingTo(Comparator.comparing(Inventory::getInventoryId))
                        .anySatisfy(i -> assertThat(i).isSameAs(managed))
                        .hasSize((int) countByFilmId(managed.getFilmId()));
            }
        }

        /**
         * Calls the method with no transaction around the test, and asserts that Spring Data refuses it, with
         * {@link InvalidDataAccessApiUsageException}, before any query runs.
         *
         * @param inventory one of the copies selected in {@link InventoryRepository_SpringBootIT#__()}.
         */
        @MethodSource("inventories")
        @ParameterizedTest
        void _InvalidDataAccessApiUsageException_NoSurroundingTransaction(final Inventory inventory) {
            assertThatThrownBy(() -> {
                try (var found = repositoryInstance().findAllByFilmIdOrderByInventoryIdAsc(inventory.getFilmId())) {
                    found.forEach(i -> {
                    });
                }
            }).isInstanceOf(InvalidDataAccessApiUsageException.class);
        }
    }

    /**
     * Tests {@link InventoryRepository#selectCopyCountsByFilm(Pageable)} ({@code SAKILA-INVENTORY-I-01}).
     */
    @Nested
    class SelectCopyCountsByFilm_Test {

        private static final Sort BY_COPIES_DESC_THEN_FILM_ID =
                Sort.by(Sort.Direction.DESC, "copies").and(Sort.by("f.filmId"));

        private static Stream<Inventory> inventories() {
            return InventoryRepository_SpringBootIT.inventories();
        }

        /**
         * Asserts that the first five, the most copied first and then by film id as the exercise sorts, are the
         * exercise's recorded output, five films of eight copies each, of the 958 films with a copy.
         */
        @Test
        void __() {
            final var page = repositoryInstance().selectCopyCountsByFilm(
                    PageRequest.of(0, 5, BY_COPIES_DESC_THEN_FILM_ID));
            assertThat(page.getContent())
                    .extracting(column("film", Film.class, Film::getFilmId), column("film", Film.class, Film::getTitle),
                                column("copies", Long.class))
                    .containsExactly(tuple(1, "ACADEMY DINOSAUR", 8L),
                                     tuple(31, "APACHE DIVINE", 8L),
                                     tuple(69, "BEVERLY OUTLAW", 8L),
                                     tuple(73, "BINGO TALENTED", 8L),
                                     tuple(86, "BOOGIE AMELIE", 8L));
            assertThat(page.getTotalElements()).isEqualTo(958L);
        }

        /**
         * Asserts that a page of the given size, sorted the exercise's way, is ordered so, that each film is the one
         * of its id, read through {@link FilmRepository}, and that each count is the number of copies of the film.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 5, 10})
        @ParameterizedTest
        void __Ordered(final int size) {
            final var page = repositoryInstance().selectCopyCountsByFilm(
                    PageRequest.of(0, size, BY_COPIES_DESC_THEN_FILM_ID));
            assertPage(page);
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(column("copies", Long.class)).reversed()
                                                 .thenComparing(column("film", Film.class, Film::getFilmId)))
                    .allSatisfy(c -> {
                        final var film = c.get("film", Film.class);
                        assertThat(filmRepository.findById(film.getFilmId())).hasValue(film);
                        assertThat(c.get("copies", Long.class)).as("copies of film %d", film.getFilmId())
                                .isEqualTo(countByFilmId(film.getFilmId()));
                    });
        }

        /**
         * Asserts that the second page of five continues the first, with the same total.
         */
        @Test
        void __Paged() {
            final var first = repositoryInstance().selectCopyCountsByFilm(
                    PageRequest.of(0, 5, BY_COPIES_DESC_THEN_FILM_ID));
            final var second = repositoryInstance().selectCopyCountsByFilm(first.nextPageable());
            final var ten = repositoryInstance().selectCopyCountsByFilm(
                    PageRequest.of(0, 10, BY_COPIES_DESC_THEN_FILM_ID));
            assertThat(second.getTotalElements()).isEqualTo(first.getTotalElements());
            assertThat(second.getContent()).extracting(column("film", Film.class))
                    .containsExactlyElementsOf(ten.getContent().subList(5, 10).stream()
                                                       .map(column("film", Film.class)).toList());
        }

        /**
         * Asserts that the film of a selected copy is among the films selected.
         *
         * @param inventory one of the copies selected in {@link InventoryRepository_SpringBootIT#__()}.
         */
        @MethodSource("inventories")
        @ParameterizedTest
        void __Selected(final Inventory inventory) {
            final var all = repositoryInstance().selectCopyCountsByFilm(Pageable.unpaged(BY_COPIES_DESC_THEN_FILM_ID));
            assertThat(all.getContent())
                    .extracting(column("film", Film.class, Film::getFilmId))
                    .contains(inventory.getFilmId());
        }
    }

    /**
     * Tests {@link InventoryRepository#listNeverRented(Pageable)} ({@code SAKILA-INVENTORY-I-02}).
     */
    @Nested
    class ListNeverRented_Test {

        /**
         * Asserts that the copies never rented are as many as the exercise's recorded output, one, and as many as all
         * copies less those with a rental, counted by the complementary {@link Specification}, {@code IS NOT EMPTY}
         * on the copy's rentals.
         */
        @Test
        void __() {
            final var page = repositoryInstance().listNeverRented(PageRequest.of(0, 5, BY_INVENTORY_ID));
            assertPage(page);
            assertThat(page.getTotalElements()).isEqualTo(1L);
            final Specification<Inventory> rented = (r, q, b) -> b.isNotEmpty(r.get(Inventory_.rentals));
            assertThat(page.getTotalElements())
                    .isEqualTo(repositoryInstance().count() - repositoryInstance().count(rented));
        }

        /**
         * Asserts that the page, sorted by id, is ordered so.
         */
        @Test
        void __Ordered() {
            final var page = repositoryInstance().listNeverRented(Pageable.unpaged(BY_INVENTORY_ID));
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(Inventory::getInventoryId));
        }

        /**
         * Asserts that a page past the last is empty, with the same total.
         */
        @Test
        void __Paged() {
            final var first = repositoryInstance().listNeverRented(PageRequest.of(0, 1, BY_INVENTORY_ID));
            final var past = repositoryInstance().listNeverRented(PageRequest.of(first.getTotalPages(), 1,
                                                                                 BY_INVENTORY_ID));
            assertThat(past).isEmpty();
            assertThat(past.getTotalElements()).isEqualTo(first.getTotalElements());
        }
    }

    /**
     * Tests {@link InventoryRepository#selectLongestIdleTimes()} ({@code SAKILA-INVENTORY-A-01}).
     */
    @Nested
    class SelectLongestIdleTimes_Test {

        /**
         * Asserts that the native query selects the exercise's recorded output: five copies, by idle days
         * descending, then by copy id.
         */
        @Test
        void __() {
            final var list = repositoryInstance().selectLongestIdleTimes();
            assertThat(list)
                    .extracting(intColumn("inventory_id"), longColumn("idleDays"))
                    .containsExactly(tuple(977, 45L),
                                     tuple(3921, 45L),
                                     tuple(506, 39L),
                                     tuple(517, 39L),
                                     tuple(934, 39L));
        }

        /**
         * Asserts that each idle time is the days between its two dates, the earlier the return, as {@code DATEDIFF}
         * counts them, by calendar dates.
         */
        @Test
        void __Ordered() {
            final var list = repositoryInstance().selectLongestIdleTimes();
            assertThat(list)
                    .isSortedAccordingTo(Comparator.comparing(longColumn("idleDays")).reversed()
                                                 .thenComparing(intColumn("inventory_id")))
                    .allSatisfy(t -> {
                        final var returnDate = t.get("return_date", LocalDateTime.class);
                        final var nextRentalDate = t.get("next_rental_date", LocalDateTime.class);
                        assertThat(returnDate).isBefore(nextRentalDate);
                        final var days = ChronoUnit.DAYS.between(returnDate.toLocalDate(),
                                                                 nextRentalDate.toLocalDate());
                        assertThat(t.get("idleDays", Number.class).longValue()).isEqualTo(days);
                    });
        }
    }

    /**
     * Tests {@link InventoryRepository#selectCopyCountsOfFilmsEveryCopyRented(Pageable)}
     * ({@code SAKILA-INVENTORY-A-02}).
     */
    @Nested
    class SelectCopyCountsOfFilmsEveryCopyRented_Test {

        private static final Sort BY_FILM_ID = Sort.by("f.filmId");

        /**
         * Asserts that the films whose every copy was rented are as many as the exercise's recorded output, 957 of
         * the 958 films with a copy.
         */
        @Test
        void __() {
            final var page = repositoryInstance().selectCopyCountsOfFilmsEveryCopyRented(
                    PageRequest.of(0, 5, BY_FILM_ID));
            assertPage(page);
            assertThat(page.getTotalElements()).isEqualTo(957L);
            final var withCopy = repositoryInstance().selectCopyCountsByFilm(PageRequest.of(0, 1, BY_FILM_ID));
            assertThat(withCopy.getTotalElements()).isEqualTo(958L);
        }

        /**
         * Asserts, in one transaction, that a page by film id is ordered so, that each count is the number of copies
         * of the film, and that no copy of a selected film is among the copies never rented.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 5, 10})
        @ParameterizedTest
        @Transactional
        void __Ordered(final int size) {
            final var page = repositoryInstance().selectCopyCountsOfFilmsEveryCopyRented(
                    PageRequest.of(0, size, BY_FILM_ID));
            assertPage(page);
            final var neverRented = repositoryInstance().listNeverRented(Pageable.unpaged(BY_INVENTORY_ID))
                    .getContent().stream().map(Inventory::getFilmId).toList();
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(column("film", Film.class, Film::getFilmId)))
                    .allSatisfy(c -> {
                        final var film = c.get("film", Film.class);
                        assertThat(c.get("copies", Long.class)).isEqualTo(countByFilmId(film.getFilmId()));
                        assertThat(neverRented).doesNotContain(film.getFilmId());
                    });
        }

        /**
         * Asserts that the film of a copy never rented is not selected, however many pages are walked.
         */
        @Test
        void __Paged() {
            final var neverRented = repositoryInstance().listNeverRented(Pageable.unpaged(BY_INVENTORY_ID))
                    .getContent().stream().map(Inventory::getFilmId).toList();
            final var all = repositoryInstance().selectCopyCountsOfFilmsEveryCopyRented(Pageable.unpaged(BY_FILM_ID));
            assertThat(all.getContent()).hasSize((int) all.getTotalElements())
                    .extracting(column("film", Film.class, Film::getFilmId))
                    .doesNotContainAnyElementsOf(neverRented);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private FilmRepository filmRepository;
}
