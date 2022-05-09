package net.metacheck.website_parser

import io.github.cdimascio.essence.Link


data class ScrapeResult(
  val url: String,
  val id: String,
  val title: String,
  val description: String,
  val featuredImage: String?,
  val wordCount: Int,
  val hasDuplicates: Boolean,
  val heading: String?,
  val headings: ArrayList<ScrapedHeading>,
  val text: String,
  val links: List<Link>,

  )

data class ScrapedHeading(val value: String, val name: String, val occurences: Int = 1)
