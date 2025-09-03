from translations import strings


def main():
    words = strings()
    xml = (
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
        "<strings>\n"
    )
    for word in words:
        xml += (
            "    <string>\n"
            f"        <original>{word}</original>\n"
            "        <translation></translation>\n"
            "    </string>\n"
        )
    xml += "</strings>"

    print(xml)


if __name__ == "__main__":
    main()
