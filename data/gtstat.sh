echo "Number of pope tags:"
cat * | grep -ci "<RP Category='POPE_TAG'>"

echo "Number of monarch tags:"
cat * | grep -ci "<RP Category='MONARCH_TAG'>"

echo "Number of headofState tags:"
cat * | grep -ci "<RP Category='HEAD_OF_STATE_TAG'>"

echo "Number of chairPerson tags:"
cat * | grep -ci "<RP Category='CHAIR_PERSON_TAG'>"

echo "Number of all tags:"
cat * | grep -ci "<RP"
