# cordsync

Sync values between configuration files.

## Usage

```bash
cordsync              # Use default sync-rules.edn
cordsync -c custom.edn  # Use custom configuration
cordsync init         # Create sync-rules.edn template
cordsync --help       # Show help
```

## Configuration Format

Create a `sync-rules.edn` file:

```clojure
{:sources {:meta "../meta.json"}
 :targets [{:file "Cargo.toml"
            :replacements [{:pattern "^name = \".*\""
                           :template "name = \"${meta.name}\""}
                          {:pattern "^version = \".*\""
                           :template "version = \"${meta.version}\""}]}]}
```