package net.metacheck.website_parser


data class ScrapeResult(
  val title: String,
  val description: String,
  val featuredImage: String?,
  val wordCount: Int,
  val heading: String?,
  val headings: ArrayList<ScrapedHeading>
)

data class ScrapedHeading(val value: String, val name: String)
