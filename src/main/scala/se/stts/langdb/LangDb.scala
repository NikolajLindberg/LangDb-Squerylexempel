package se.stts.langdb

import org.squeryl._
import org.squeryl.dsl._
import dsl.CompositeKey2
import annotations._

/* Denna importrad ger tillgång till Squeryls 'oneToManyRelation', '===', etc... */
import org.squeryl.PrimitiveTypeMode._ 

/**************************************************************************
 * Detta är den fullständiga koden till artikeln om Squeryl i Datormagazin
 * nummer 11, 2010. LangDb.scala är ett litet exempel som illustrerar hur
 * man använder Squeryl. Se även testklasserna, MediumDataSample.scala,
 * MediumDataSampleTest.scala och ArtikelKodTest.scala
 * 
 *                av Nikolaj Lindberg och Hanna Lindgren
 **************************************************************************
 */

/**
 * Genom att implementera detta trait, <code>AutoId</code>,
 * skapas en primärnyckel, <code>id</code> av typen Int, som
 * räknas upp automatiskt.
 *
 * (Scalas 'trait' är ett koncept för att återanvända kod på ett flexibelt sätt.)
 *
 * @author Nikolaj Lindberg, Hanna Lindgren
 */
trait AutoId extends KeyedEntity[Int] {

  /**
   * Tabellens primärnyckel
   */
  val id: Int = 0
}


/**
 * En 'case-klass' som representerar ett språk.
 *
 * Scalas 'case-klass' ger lite extra funktionalitet. Några exempel:
 * <ul>
 * <li><code>equals</code> och <code>hashCode</code></li>
 * <li>getters/setters</li>
 * <li><code>toString</code></li>
 * </ul>
 * 
 * @param iso Språkets ISO-kod (tre tecken, ISO 639-2, gemener)
 * @param enName Språkets engelska namn
 * @param cBlock Språkets 'code block' (teckenuppsättning),
 *               till exempel LATIN eller CYRILLIC.
 * 
 *
 * @author Nikolaj Lindberg, Hanna Lindgren
 */

// Notera hur man kan definiera maxlängd (bland annat) på en sträng med hjälp av @Column 
case class Lang(@Column(length=3) iso: String, 
		enName: String, 
		cBlock: Option[String]) extends AutoId {

  /**
   * I Scala definieras hjälpkonstruktorer med hjälp av <code>def this()</code>.
   * 
   * Om standard-konstruktorn tar 'Option'-parametrar, behöver Squeryl en
   * extra konstruktor utan argument. Detta kommer att försvinna i framtida
   * versioner av Squeryl. */
   def this() = this("", "", Some(""))
 
  /**
   * Koppling till språkets 'alternativa' namn via <em>en till många</em>-relationen
   * <code>langToAltNames</code>
   */
  lazy val aNames: OneToMany[AltName] = LangDb.langToAltNames.left(this)

  /**
   * Koppling till språkfamiljer namn via <em>många till många</em>-relationen
   * <code>langsAndFamilies</code>
   */
  lazy val families = LangDb.langsAndFamilies.left(this)

  /**
   * Koppling till länder via <em>många till många</em>-relationen
   * <code>langsAndCountries</code>
   */
  lazy val countries = LangDb.langsAndCountries.left(this)
}

/**
 * Alternativa (icke-engelska) namn på språk.
 * @param name Namnet
 * @param langId Språkets databas-id (se <code>Lang</code>)
 *
 * @author Nikolaj Lindberg, Hanna Lindgren
 */
case class AltName(name: String, langId: Int = 0) extends AutoId {
  /**
   * Koppling till språket via <em>en till många</em>-relationen
   * <code>langToAltNames</code>
   */ 
  lazy val enLang: Lang = LangDb.langToAltNames.right(this).single
}

/**
 * Ett land.
 * @param iso Landets ISO-kod (två tecken, ISO 3166-1 alpha 2, versaler)
 * @param enName Landets namn
 * @param area Region/världsdel
 *
 * @author Nikolaj Lindberg, Hanna Lindgren
 */
case class Country(@Column(length=2) iso: String, 
		   enName: String, area: Option[String]) extends AutoId 
{
  /** Extra konstruktor för Squeryl */
  def this() = this("", "", Some("")) 
}


/**
 * En språkfamilj (indoeuropeiska, germanska, arabiska, etc)
 * @param name Språkfamiljens namn
 * @param iso Språkfamiljens ISO-kod (tre tecken, ISO 639-5, gemener)
 *
 * @author Nikolaj Lindberg, Hanna Lindgren
 */
case class Family(name: String, 
		  @Column(length=3) iso: Option[String]) extends AutoId 
{
  /** Extra konstruktor för Squeryl */
  def this() = this("", Some(""))
}

/**
 * Associationstabell mellan <code>Lang</code> och <code>Country</code>,
 * mellan vilka det råder en <em>många till många</em>-relation.
 * @param official Anger om språket har officiell status i aktuellt land
 * @param speakers Antalet talare av språket, i aktuellt land
 * @param langId Språkets databas-id
 * @param countryId Landets databas-id
 *
 * @author Nikolaj Lindberg, Hanna Lindgren
 */
case class LangAndCountry(official: Option[Boolean], speakers: Option[Long], 
                          langId: Int = 0, countryId: Int = 0) 
  extends KeyedEntity[CompositeKey2[Int,Int]] {

  /** Extra konstruktor för Squeryl */
  def this() = this(Some(false), Some(0), 0, 0)

   /** Sammansatt databasnyckel - <code>countryId</code> och
    * <code>langId</code> är tillsammans unika i tabellen. */
  def id = compositeKey(countryId, langId)
}

/**
 * Associationstabell mellan <code>Lang</code> och <code>Family</code>,
 * mellan vilka det råder en <em>många till många</em>-relation.
 * @param langId Språkets databas-id
 * @param familyId Språkfamiljens databas-id
 *
 * @author Nikolaj Lindberg, Hanna Lindgren
 */
case class LangAndFamily(langId: Int = 0, familyId: Int = 0) 
  extends KeyedEntity[CompositeKey2[Int,Int]] {

   /** Sammansatt databasnyckel */
  def id = compositeKey(langId, familyId)
}

/**
 * Schema för databasen. Här deklareras databasens tabeller och relationerna dem emellan.
 *
 * Ett 'object' i Scala är en 'singleton', och används ungefär
 * som man i Java använder statiska metoder.
 * 
 * @author Nikolaj Lindberg, Hanna Lindgren
 */
object LangDb extends Schema {
 
  /* Grundläggande tabeller */

  /**
   * Tabell för <code>Lang</code>
   */
  val langs: Table[Lang] = table[Lang]

  /**
   * Tabell för <code>AltName</code>
   */
  val altNames = table[AltName]

  /**
   * Tabell för <code>Country</code>
   */
  val countries = table[Country]

  /**
   * Tabell för <code>Family</code>
   */
  val families = table[Family]

  /* 'Constraints' för några tabeller */
  on(langs)(l => declare(columns(l.iso) are(unique,indexed)))
  on(countries)(c => declare(columns(c.iso) are(unique,indexed)))
  on(families)(f => declare(
    columns(f.iso) are(unique,indexed),
    columns(f.name) are(unique,indexed)
  ))
  on(altNames)(a => declare(
    columns(a.langId, a.name) are(unique, indexed)
  ))
 
  /* Relationer/associationstabeller */
 
  /** 
   * <code>langsAndCountries</code> är en <code>ManyToManyRelation</code>
   * av typen <code>Table[LangAndCountry]</code>
   */
   val langsAndCountries = 
    manyToManyRelation(langs, countries).via[LangAndCountry]((l, c, lic) => 
      (l.id === lic.langId, c.id === lic.countryId))

  /** 
   * <code>langsAndFamilies</code> är en <code>ManyToManyRelation</code>
   * av typen <code>Table[LangAndFamily]</code>
   */
  val langsAndFamilies = 
    manyToManyRelation(langs, families).via[LangAndFamily]((l, f, laf) => 
      (l.id === laf.langId, f.id === laf.familyId))

  /** 
   * <code>langToAltNames</code> är en <code>OneToManyRelation</code>
   */
  val langToAltNames = 
    oneToManyRelation(langs, altNames).via((l, a) => l.id === a.langId)
 
  /**
   * Raderar hela databasen. Tänk efter före!
   */
  override def drop = super.drop
}




