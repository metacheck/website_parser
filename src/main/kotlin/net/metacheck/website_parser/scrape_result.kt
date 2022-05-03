package net.metacheck.website_parser


data class ScrapeResult(
  val url: String,
  val title: String,
  val description: String,
  val featuredImage: String?,
  val wordCount: Int,
  val hasDuplicates: Boolean,
  val heading: String?,
  val headings: ArrayList<ScrapedHeading>,
  val text: String,
)

data class ScrapedHeading(val value: String, val name: String, val occurences: Int = 1)
