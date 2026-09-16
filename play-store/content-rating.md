# Content rating questionnaire (IARC) — answers

Play Console → Policy → App content → **Content rating**.

Answer honestly; a rating obtained with wrong answers is grounds for removal.
These answers describe TS IPTV as it actually behaves.

---

## Category

**Entertainment** (not "Game", not "Social", not "News").

---

## Questionnaire

| Question | Answer | Why |
| --- | --- | --- |
| Violence — does the app contain violent content? | No | The app contains no content of its own. |
| Sexuality — sexual or suggestive content? | No | Same. |
| Language — profanity? | No | Same. |
| Controlled substances — references to drugs, alcohol, tobacco? | No | Same. |
| Does the app allow users to interact or exchange content? | **No** | There is no user-to-user feature: no chat, no comments, no sharing, no profiles visible to others. |
| Does the app share the user's current location with other users? | No | No location is collected at all. |
| Does the app allow users to purchase digital goods? | No | No in-app purchases, no billing library. |
| Does the app contain ads? | **Yes** | Shopee affiliate offers. |
| Is the app a web browser or search engine? | **No** | It fetches a URL the user enters, but it renders a channel list, not arbitrary web pages. |
| Can users access the internet through this app in an unrestricted way? | **No** | The app fetches playlist, EPG and stream URLs only. |
| Does the app contain user-generated content that is publicly visible? | No | Playlists a user adds are visible only to that user, on that device. |

---

## The question that matters for an IPTV app

There is no IARC question that asks "can the user point this at content you do
not control?" — but that is the thing a human reviewer will think about.

Be ready to answer it in the **review notes** field. Suggested wording:

> TS IPTV is a playlist player. It ships with no channels, no bundled
> playlists, and no directory of sources. It parses playlist formats (M3U,
> XSPF, JSON, XMLTV) supplied by the user, in the same way a media player parses
> a video file. The app does not host, index, aggregate or recommend any stream,
> and it has no server-side catalogue. Content responsibility rests with the
> user, as stated in the store listing, on the landing page, and in the Terms of
> Use at https://tsiptv-8bdd6.web.app/terms/.

---

## Expected outcome

- **IARC generic:** 12+
- **ESRB:** Everyone 10+ or Teen
- **PEGI:** 12
- **USK:** 12

Driven mainly by the "contains ads" answer and by the app being an open-ended
media player. Set **Target audience** to 13 and over, and do not opt into the
Designed for Families programme — a player that can point anywhere is a poor fit
for it and the extra scrutiny is not worth it.
