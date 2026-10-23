package com.github.jinahya.mysql.sakila.data;

import com.github.jinahya.mysql.sakila.persistence.Actor;
import com.github.jinahya.mysql.sakila.persistence.Address;
import com.github.jinahya.mysql.sakila.persistence.Category;
import com.github.jinahya.mysql.sakila.persistence.City;
import com.github.jinahya.mysql.sakila.persistence.Country;
import com.github.jinahya.mysql.sakila.persistence.Film;
import com.github.jinahya.mysql.sakila.persistence.FilmActor;
import com.github.jinahya.mysql.sakila.persistence.FilmCategory;
import com.github.jinahya.mysql.sakila.persistence.Language;
import com.github.jinahya.object.randomizer.ObjectRandomizerUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;

import java.util.UUID;

/**
 * Persists the fixtures a {@code @DataJpaTest} needs, through the repositories, never through the entity manager.
 * <p>
 * Each method randomizes an entity with the upstream randomizer, gives it whatever it refers to (persisted the same
 * way, or passed in), and saves it through that entity's repository. Only the entities that can be persisted into an
 * empty H2 are here: a store and its manager refer to each other, both {@code NOT NULL}, so neither a store nor a
 * staff member can be saved first, nor anything that needs a store (a customer, a copy, a rental, a payment); and
 * {@code film_text} is read-only. The tests of those repositories run their queries against empty tables.
 * <p>
 * {@link _Repository_DataJpaTest} imports it; a slice test reaches it as {@code fixtures()}.
 */
@TestComponent
class _Repository_DataJpaTest_Fixtures {

    /**
     * Returns a randomized instance of the specified class, through its upstream {@code <Entity>_Randomizer}.
     *
     * @param clazz the class to randomize.
     * @param <T>   the type of the class.
     * @return a randomized instance.
     */
    static <T> T randomized(final Class<T> clazz) {
        return ObjectRandomizerUtils.newRandomizedInstanceOf(clazz).orElseThrow();
    }

    /**
     * Returns a name no other test uses: a random UUID, 36 characters, within the 45 of the name columns.
     *
     * @return a random name.
     */
    static String randomName() {
        return UUID.randomUUID().toString();
    }

    // -----------------------------------------------------------------------------------------------------------------

    Country country() {
        return countryRepository.save(randomized(Country.class));
    }

    City city(final Country country) {
        final var city = randomized(City.class);
        city.setCountry(country);
        return cityRepository.save(city);
    }

    City city() {
        return city(country());
    }

    Address address(final City city) {
        final var address = randomized(Address.class);
        address.setCity(city);
        return addressRepository.save(address);
    }

    Address address() {
        return address(city());
    }

    Category category() {
        return categoryRepository.save(randomized(Category.class));
    }

    Language language() {
        return languageRepository.save(randomized(Language.class));
    }

    /**
     * Saves a randomized film in the specified language, with no original language.
     *
     * @param language the language of the film.
     * @return the saved film.
     */
    Film film(final Language language) {
        final var film = randomized(Film.class);
        film.setLanguage(language);
        film.setOriginalLanguage(null);
        return filmRepository.save(film);
    }

    Film film() {
        return film(language());
    }

    Actor actor() {
        return actorRepository.save(randomized(Actor.class));
    }

    /**
     * Saves the specified actor as an actor of the specified film.
     *
     * @param actor the actor.
     * @param film  the film.
     * @return the saved link; the id is derived from the two, so what comes back may be a merged copy.
     */
    FilmActor filmActor(final Actor actor, final Film film) {
        return filmActorRepository.save(FilmActor.of(actor, film));
    }

    /**
     * Saves the specified film as a film of the specified category.
     *
     * @param film     the film.
     * @param category the category.
     * @return the saved link; the id is derived from the two, so what comes back may be a merged copy.
     */
    FilmCategory filmCategory(final Film film, final Category category) {
        return filmCategoryRepository.save(FilmCategory.of(film, category));
    }

    // -----------------------------------------------------------------------------------------------------------------
    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private LanguageRepository languageRepository;

    @Autowired
    private FilmRepository filmRepository;

    @Autowired
    private ActorRepository actorRepository;

    @Autowired
    private FilmActorRepository filmActorRepository;

    @Autowired
    private FilmCategoryRepository filmCategoryRepository;
}
