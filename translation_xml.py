from translations import strings

words = strings()
xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\
<strings>\n"
for word in words:
    xml += "    <string>\n        <original>"+word+"</original>\n        <translation></translation>\n    </string>\n"
xml += "</strings>"

print(xml)
