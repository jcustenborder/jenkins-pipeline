def getConnectors() {
    def result = [
            'kafka-connect-memcached'        : 'jcustenborder/kafka-connect-memcached/master',
            'kafka-connect-redis'            : 'jcustenborder/kafka-connect-redis/master',
            'kafka-connect-simulator'        : 'jcustenborder/kafka-connect-simulator/master',
            'kafka-connect-solr'             : 'jcustenborder/kafka-connect-solr/master',
            'kafka-connect-splunk'           : 'jcustenborder/kafka-connect-splunk/master',
            'kafka-connect-spooldir'         : 'jcustenborder/kafka-connect-spooldir/master',
            'kafka-connect-syslog'           : 'jcustenborder/kafka-connect-syslog/master',
            'kafka-connect-twitter'          : 'jcustenborder/kafka-connect-twitter/master',

            'kafka-connect-transform-archive': 'jcustenborder/kafka-connect-transform-archive/master',
            'kafka-connect-transform-cef'    : 'jcustenborder/kafka-connect-transform-cef/master',
            'kafka-connect-transform-common' : 'jcustenborder/kafka-connect-transform-common/master',
            'kafka-connect-transform-maxmind': 'jcustenborder/kafka-connect-transform-maxmind/master',
            'kafka-connect-transform-xml'    : 'jcustenborder/kafka-connect-transform-xml/master'
    ]

    return result
}