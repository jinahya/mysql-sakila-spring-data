package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.StaffList;
import com.github.jinahya.mysql.sakila.persistence.StaffList_;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests {@link StaffListRepository} against the Sakila database, through {@link _Repository_SpringBootIT}.
 * <p>
 * See that class for the context and the transactions. The tests here only read: the fixture is the rows of the
 * view, selected once in {@link #__()}, which in the sample data are its two rows, and each test checks a query of
 * the exercises against them, or against the {@code staff} table the view is built on, read through
 * {@link StaffRepository}. The base's own tests, such as its {@code findById} round trips, run here too.
 * <p>
 * Needs the database of {@code application.yaml} up, with Sakila loaded; see that file.
 *
 * @see StaffListRepository_DataJpaTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StaffListRepository_SpringBootIT
        extends _Repository_SpringBootIT<StaffListRepository, StaffList, Integer> {

    private static List<StaffList> entities;

    /**
     * A sort by id; by the attribute's name, since the metamodel's {@code StaffList_.id} is filled in only once the
     * context is up, after this class is loaded.
     */
    private static final Sort BY_ID = Sort.by(Sort.Direction.ASC, StaffList_.ID);

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Creates a new instance.
     */
    StaffListRepository_SpringBootIT() {
        super(StaffListRepository.class, StaffList.class, Integer.class);
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Selects the first five rows of the view, by id, as the fixture for the parameterized tests.
     * <p>
     * Ordered by id so that the rows are the ones of the first staff members the init scripts put there; the sample
     * data has two. Runs once per class, which {@link TestInstance.Lifecycle#PER_CLASS} allows to be an instance
     * method, so the autowired repository is already there; the result goes into a static field so that the static
     * method sources of the nested classes can reach it. The rows are loaded outside any transaction, so they are
     * detached.
     * <p>
     * An empty view aborts the tests of this class rather than failing them: it is the fixture that is missing, not
     * the behaviour under test that is wrong.
     */
    @BeforeAll
    void __() {
        entities = Collections.unmodifiableList(
                repositoryInstance().findAll(PageRequest.of(0, 5, BY_ID)).getContent()
        );
        assumeThat(entities).isNotEmpty();
    }

    private static Stream<StaffList> staffLists() {
        return entities.stream();
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
     * Tests {@link StaffListRepository#listAll(Pageable)} ({@code SAKILA-STAFF_LIST-B-01}).
     */
    @Nested
    class ListAll_Test {

        private static Stream<StaffList> staffLists() {
            return StaffListRepository_SpringBootIT.staffLists();
        }

        /**
         * Asserts that a page of the given size, by id as the exercise sorts, is ordered so, holds as many rows in
         * all as the {@code staff} table has staff members, and names each of them as the table does, first name and
         * last name as one string.
         *
         * @param size the size of the page.
         */
        @ValueSource(ints = {1, 2, 5})
        @ParameterizedTest
        void __(final int size) {
            final var page = repositoryInstance().listAll(PageRequest.of(0, size, BY_ID));
            assertPage(page);
            assertThat(page.getTotalElements()).isEqualTo(staffRepository.count());
            assertThat(page.getContent())
                    .isNotEmpty()
                    .isSortedAccordingTo(Comparator.comparing(StaffList::getId))
                    .allSatisfy(l -> {
                        final var staff = staffRepository.findById(l.getId()).orElseThrow();
                        assertThat(l.getName())
                                .isEqualToIgnoringCase(staff.getFirstName() + ' ' + staff.getLastName());
                        assertThat(l.getSid()).isEqualTo(staff.getStore().getStoreId());
                    });
        }

        /**
         * Asserts that every row lists a selected row, and that pages of one walk the same rows in the same order.
         *
         * @param staffList one of the rows selected in {@link StaffListRepository_SpringBootIT#__()}.
         */
        @MethodSource("staffLists")
        @ParameterizedTest
        void __Selected(final StaffList staffList) {
            final var all = repositoryInstance().listAll(Pageable.unpaged(BY_ID));
            assertThat(all.getContent()).contains(staffList);
            var page = repositoryInstance().listAll(PageRequest.of(0, 1, BY_ID));
            assertThat(page.getTotalElements()).isEqualTo(all.getTotalElements());
            for (final var expected : all) {
                assertThat(page.getContent()).containsExactly(expected);
                if (page.hasNext()) {
                    page = repositoryInstance().listAll(page.nextPageable());
                }
            }
        }
    }

    /**
     * Tests {@link StaffListRepository#selectRowCounts()} ({@code SAKILA-STAFF_LIST-I-01}).
     */
    @Nested
    class SelectRowCounts_Test {

        private static Stream<StaffList> staffLists() {
            return StaffListRepository_SpringBootIT.staffLists();
        }

        /**
         * Asserts that the view has as many rows as the {@code staff} table, as the exercise checks, that each count
         * is what the view's own repository, and {@link StaffRepository}, count, and that the selected row's staff
         * member is in the table, which is what the equality says of every row.
         *
         * @param staffList one of the rows selected in {@link StaffListRepository_SpringBootIT#__()}.
         */
        @MethodSource("staffLists")
        @ParameterizedTest
        void __(final StaffList staffList) {
            final var counts = repositoryInstance().selectRowCounts();
            assertThat(counts.getStaffLists()).isPositive().isEqualTo(counts.getStaffs());
            assertThat(counts.getStaffLists()).isEqualTo(repositoryInstance().count());
            assertThat(counts.getStaffs()).isEqualTo(staffRepository.count());
            assertThat(staffRepository.existsById(staffList.getId())).isTrue();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private StaffRepository staffRepository;
}
