# FluffyDailyRewards

A Daily Rewards plugin for Paper, Spigot, and Folia with MySQL and SQLite support, featuring a fully customizable daily check-in system.

## Commands

### For Players
| Command | Permission | Description |
|---------|-----------|-------------|
| `/daily` | `fluffydailyrewards.use` | Open daily rewards menu |

**Aliases:** `/dailyreward`, `/dr`

### For Admins
| Command | Permission | Description |
|---------|-----------|-------------|
| `/daily admin reload` | `fluffydailyrewards.admin` | Reload configuration |
| `/daily admin give <player> <day>` | `fluffydailyrewards.admin` | Give reward to player |
| `/daily admin reset <player>` | `fluffydailyrewards.admin` | Reset player data |
| `/daily admin set <player> <day>` | `fluffydailyrewards.admin` | Set player's current day |
| `/daily admin check <player>` | `fluffydailyrewards.admin` | Check player data |

## PlaceholderAPI Support

FluffyDailyRewards integrates with PlaceholderAPI. All placeholders use the prefix `%daily_<placeholder>%`

### Available Placeholders

#### Player Statistics
| Placeholder | Description | Example Output |
|------------|-------------|----------------|
| `%daily_streak%` | Current streak (consecutive days) | `5` |
| `%daily_total_claims%` | Total number of claims | `23` |
| `%daily_next_day%` | Next day to claim | `6` |
| `%daily_max_day%` | Maximum day configured | `7` |
| `%daily_can_claim%` | Can claim today (true/false) | `true` |
| `%daily_can_claim_formatted%` | Can claim today (Yes/No) | `Yes` |
| `%daily_last_claim%` | Time since last claim | `2h 30m ago` |
| `%daily_last_claim_date%` | Last claim date/time | `12/02/2026 14:30:45` |
| `%daily_next_claim%` | Time until next claim | `21h 30m` |
| `%daily_next_claim_timestamp%` | Unix timestamp of next claim | `1739308800000` |
| `%daily_status%` | Full status message | `Next claim available in: 21h` |
| `%daily_progress%` | Current progress | `5/7` |

#### Leaderboard Placeholders (Streak)
| Placeholder | Description | Example Output |
|------------|-------------|----------------|
| `%daily_top_streak_name_<number>%` | Player name at position (1-max) | `Steve` |
| `%daily_top_streak_value_<number>%` | Streak value at position (1-max) | `15` |
| `%daily_top_streak_rank%` | Current player's streak rank | `3` |

#### Leaderboard Placeholders (Total Claims)
| Placeholder | Description | Example Output |
|------------|-------------|----------------|
| `%daily_top_total_claims_name_<number>%` | Player name at position (1-max) | `Alex` |
| `%daily_top_total_claims_value_<number>%` | Total claims at position (1-max) | `150` |
| `%daily_top_total_claims_rank%` | Current player's total claims rank | `7` |

---

**Made with ❤️ By MasterN**
